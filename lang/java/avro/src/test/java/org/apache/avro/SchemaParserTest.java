package org.apache.avro;

import org.junit.Before;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SchemaParserTest {

  private Schema.Parser parser;

  @Before
  public void setUp() {
    //inzializzo il parser
    parser = new Schema.Parser();
  }

  @After
  public void tearDown() {
    parser = null;
  }


  //Verifico il parsing della forma più semplice possibile
  @Test
  public void TC01_parseSimplePrimitive() {
    // "string"
    String schemaJson = "\"string\"";
    Schema schema = parser.parse(schemaJson);
    assertNotNull(schema);
    assertEquals(Schema.Type.STRING, schema.getType());
  }

  //voglio verificare la forma del primitivo
  @Test
  public void TC02_parsePrimitiveAsObject() {
    // {"type":"int"}
    String schemaJson = "{\"type\": \"int\"}";
    Schema schema = parser.parse(schemaJson);
    assertNotNull(schema);
    assertEquals(Schema.Type.INT, schema.getType());
  }

  // voglio verificare la robustezza contro JSON non validi
  @Test
  public void TC03_parseMalformedJson() {
    /* {"type": "record",
        "name": "Test"
         manca la parentesi a chiudere
     */
    String malformedJson = "{\"type\": \"record\", \"name\": \"Test\"";
    assertThrows(SchemaParseException.class, () -> {
      parser.parse(malformedJson);
    }, "Dovrebbe lanciare eccezione per JSON malformato");
  }

  // verifico che tipi non definiti nella specifica vengano rifiutati
  @Test
  public void TC04_parseUnknownType() {
    // {"type": "nonExistentType"}
    String invalidTypeJson = "{\"type\": \"nonExistentType\"}";
    assertThrows(SchemaParseException.class, () -> {
      parser.parse(invalidTypeJson);
    }, "Dovrebbe lanciare eccezione per tipi sconosciuti");
  }

  // verifico record vuoto ma sintatticamente corretto, i record hanno questa forma
  @Test
  public void TC05_parseValidRecordMinimal() {
    //{"type":"record","name":"User","fields": []}
    String json = "{\"type\":\"record\", \"name\":\"User\", \"fields\":[]}";
    Schema schema = parser.parse(json);
    assertEquals(Schema.Type.RECORD, schema.getType());
    assertEquals("User", schema.getName());
  }

  // Verifico che il nome venga rifiutato se non è della forma specificata dalla documentazione
  @Test
  public void TC06_parseInvalidNameStartingWithNumber() {
    //{"type":"record","name":"1User","fields":[]}
    String json = "{\"type\":\"record\", \"name\":\"1User\", \"fields\":[]}";
    assertThrows(SchemaParseException.class, () -> {
      parser.parse(json);
    }, "I nomi non possono iniziare con un numero");
  }

  // Verifico che il nome venga rifiutato se non è della forma specificata dalla documentazione
  @Test
  public void TC07_parseInvalidNameWithHyphen() {
    //{"type":"record","name":"User-Name","fields":[]}
    String json = "{\"type\":\"record\", \"name\":\"User-Name\", \"fields\":[]}";
    assertThrows(SchemaParseException.class, () -> {
      parser.parse(json);
    }, "I nomi non possono contenere trattini");
  }

  // verifico che il fullName coincide con la forma riportata dalla documentazione, ossia namespace.name
  @Test
  public void TC08_parseValidNamespace() {
    // {"type":"record","name":"User","namespace":"com.example","fields":[]}
    String json = "{\"type\":\"record\", \"name\":\"User\", \"namespace\":\"com.example\", \"fields\":[]}";
    Schema schema = parser.parse(json);
    assertEquals("com.example.User", schema.getFullName());
  }

 // Verifico che venga lanciata un eccezione se non inserisco il type nel fields (il solo name è invalido)
  @Test
  public void TC09_parseRecordFieldMissingType() {
    // {"type":"record","name":"Rec","fields":["name":"field1"]}
    String json = "{\"type\":\"record\", \"name\":\"Rec\", \"fields\":[{\"name\":\"field1\"}]}";
    assertThrows(SchemaParseException.class, () -> {
      parser.parse(json);
    }, "Un campo deve avere un tipo definito");
  }

  @Test
  public void TC10_parseValidArray(){
    String json="{\"type\":\"array\", \"items\":\"string\", \"default\":[]}";
    Schema schema=parser.parse(json);
    assertEquals(Schema.Type.ARRAY, schema.getType());
  }

  // La specifica dice che le union :"Unions may not contain more than one schema with the same type", verifico quindi che tale
  // condizione venga rispettata
  @Test
  public void TC11_parseUnionWithDuplicates() {

    String json = "[\"string\", \"string\"]";
    assertThrows(AvroRuntimeException.class, () -> {
      parser.parse(json);
    }, "Le union non possono contenere tipi duplicati");
  }

  @Test
  // la specifica riporta (sempre riguardo le union): "Unions may not immediately contain other unions", verifico quindi
  // che venga rispettato
  public void TC12_parseNestedUnion() {
    // union esterna: [null, union interna]
    String json = "[\"null\", [\"string\", \"int\"]]";
    assertThrows(AvroRuntimeException.class, () -> {
      parser.parse(json);
    }, "Le union non possono essere nidificate direttamente");
  }

  // La specifica dice che non si possono ridefinire un tipo già definito nel parserr ( con lo stesso nome), verifico che ciò venga rispettato
  @Test
  public void TC13_redefinitionOfSameName() {
    String json1 = "{\"type\":\"record\", \"name\":\"Shared\", \"fields\":[]}";
    // Prima definizione OK
    parser.parse(json1);

    // Seconda definizione identica con stesso nome
    assertThrows(SchemaParseException.class, () -> {
      parser.parse(json1);
    }, "Non si può ridefinire un tipo già definito nel parser");
  }

  @Test
  public void TC14_referenceMissingType() {
    String json = "{\"type\":\"record\", \"name\":\"A\", \"fields\":[{\"name\":\"f\",\"type\":\"Missing\"]}";
    assertThrows(SchemaParseException.class, () -> {
      parser.parse(json);
    }, "Non si può far riferimento ad un tipo mancante");
  }

  // verifico il supporto alle strutture dati ricorsive
  @Test
  public void TC15_recursiveRecord() {
    // Definisco Node, che ha un campo 'next' di tipo (Node o null), il null è obbligatorio poichè la struttura ricorsiva
    // deve avere una via di uscita
    String json = "{" +
      "\"type\": \"record\", " +
      "\"name\": \"Node\", " +
      "\"fields\": [" +
      "  {\"name\": \"value\", \"type\": \"int\"}," +
      "  {\"name\": \"next\", \"type\": [\"null\", \"Node\"]}" +
      "]" +
      "}";
    Schema schema = parser.parse(json);
    assertNotNull(schema);
    assertEquals("Node", schema.getName());

    // Verifica il campo 'next' punta allo schema 'Node' (è una union)
    Schema nextFieldSchema = schema.getField("next").schema();
    // La union ha 2 tipi: null e Node. Verifichiamo il riferimento
    assertEquals(Schema.Type.UNION, nextFieldSchema.getType());
  }

  // verifico che in una situazione ammissibile tutto vada a buon fine
  @Test
  public void TC16_parseValidDefaultValue() {
    // {"type":"record","name":"R","fields":["name":"f","type":"int","default":0]}
    String json = "{\"type\":\"record\",\"name\":\"R\",\"fields\":[{\"name\":\"f\",\"type\":\"int\",\"default\":0}]}";
    assertDoesNotThrow(() -> parser.parse(json));
  }

  // verifico che venga lanciata eccezione quando come tipo del campo definisco un intero ma passo una stringa
  @Test
  public void TC17_parseInvalidDefaultTypeMismatch() {
    // {"type":"record","name":"R","fields":["name":"f","type":"int","default":"zero"]}
    String json = "{\"type\":\"record\",\"name\":\"R\",\"fields\":[{\"name\":\"f\",\"type\":\"int\",\"default\":\"zero\"}]}";
    assertThrows(AvroTypeException.class, () -> {
      parser.parse(json);
    }, "Il valore di default deve corrispondere al tipo del campo");
  }

  // verifico che il json venga accettato, anche se sintatticamente invalido, disabilitando il flag di verifica di validità
  @Test
  public void TC18_parseInvalidDefaultValidationDisabled() {
    // disabilito validazione
    parser.setValidateDefaults(false);

    // lo stesso JSON che falliva in TC17 ora deve passare
    String json = "{\"type\":\"record\",\"name\":\"R\",\"fields\":[{\"name\":\"f\",\"type\":\"int\",\"default\":\"zero\"}]}";
    Schema schema = parser.parse(json);
    assertNotNull(schema);
    assertFalse(parser.getValidateDefaults());
  }


  // la specifica riguardo gli enum dice: "All symbols in an enum must be unique; duplicates are prohibited", verifico che sia rispettata
  @Test
  public void TC19_parseEnumDuplicateSymbols() {
    // {"type":"enum","name":"Suits","symbol":["SPADES","SPADES"]}
    String json = "{\"type\":\"enum\", \"name\":\"Suits\", \"symbols\":[\"SPADES\", \"SPADES\"]}";
    assertThrows(SchemaParseException.class, () -> {
      parser.parse(json);
    }, "I simboli enum devono essere univoci");
  }

  // la specifica riguardo gli enum dà anche dei criteri su come essi devono essere strutturati, verifico che siano rispettati
  @Test
  public void TC20_verifyEnumStartsNameWithNumber(){
    String json = "{\"type\":\"enum\", \"name\":\"Suits\", \"symbols\":[\"1SPADES\", \"DIAMONDS\"]}";
    assertThrows(SchemaParseException.class, () -> {
      parser.parse(json);
    }, "I simboli non possono iniziare con un numero");
  }

  // verifico ancora che i symbol non contengano caratteri vietati dalla specifica
  @Test
  public void TC21_verifyEnumContainsInvalidCharacters(){
    String json = "{\"type\":\"enum\", \"name\":\"Suits\", \"symbols\":[\"SPADES\", \"DIAMO-NDS\"]}";
    assertThrows(SchemaParseException.class, () -> {
      parser.parse(json);
    },"i simboli non possono contenere caratteri speciali");
  }

  // verifico che quando è tutto regolare con un enum non ci siano problemi
  @Test
  public void TC22_VerifyHappyWithEnum(){
    // lo stesso JSON che falliva in TC17 ora deve passare
    String json = "{\"type\":\"enum\",\"name\":\"SUITS\",\"symbols\":[\"SPADES\", \"DIAMONDS\"]}";
    Schema schema = parser.parse(json);
    assertNotNull(schema);
  }

  // Verifico che quando dichiaro un tipo enum, se i campi obbligatori non ci sono => si verifichi un eccezione
  // come campo che è richiesto obbligatoriamente,ma non lo inserisco, scelgo il symbols
  @Test
  public void TC23_verifyEnumObligatoryFields(){
    String json = "{\"type\":\"enum\", \"name\":\"Suits\"}";
    assertThrows(SchemaParseException.class, () -> {
      parser.parse(json);
    },"i simboli non possono contenere caratteri speciali");
  }

  // Campo fixed: BVA sulla size passando un valore negativo
  @Test
  public void TC24_parseFixedNegativeSize() {
    String json = "{\"type\":\"fixed\", \"name\":\"Md5\", \"size\": -16}";
    assertThrows(IllegalArgumentException.class, () -> {
      parser.parse(json);
    }, "La dimensione Fixed deve essere positiva");
  }

  // Campo fixed: BVA sulla size passando come valore 0
  @Test
  @Ignore("Issue, con una size 0 è come se questo campo non ci fosse, ma non viene lanciata alcuna eccezione")
  public void TC25_parseFixedZoSize() {
    String json = "{\"type\":\"fixed\", \"name\":\"Md5\", \"size\": 0}";
    assertThrows(IllegalArgumentException.class, () -> {
      parser.parse(json);
    }, "La dimensione Fixed deve essere positiva");
  }

  // Campo fixed con size > 0
  @Test
  public void TC26_parseFixedRightSize() {
    String json = "{\"type\":\"fixed\", \"name\":\"Md5\", \"size\": 100}";
    Schema schema=parser.parse(json);
    assertNotNull(schema);

  }

  @Test
  public void TC27_sharedStateReference() {
    //  definisco un json A, poi definisco uno B che usa A entrambi devono funzionare in sequenza
    String jsonA = "{\"type\":\"fixed\", \"name\":\"Hash\", \"size\": 16}";
    parser.parse(jsonA);

    // Ora B usa "Hash" ( il nome di json A) e lo puo fare perche la domunetazione dice che prima esso deve essere definito
    // e poi usato
    String jsonB = "{\"type\":\"record\", \"name\":\"User\", \"fields\":[{\"name\":\"id\", \"type\":\"Hash\"}]}";
    Schema schemaB = assertDoesNotThrow(() -> parser.parse(jsonB));

    // verifico che B abbia risolto "Hash" correttamente
    assertEquals("Hash", schemaB.getField("id").schema().getName());
  }


}
