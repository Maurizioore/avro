package org.apache.avro;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SchemaParserLLMTest {
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

  // Goal: Verificare il parsing corretto di una Map
  @Test
  public void TC28_parseValidMap() {
    // Spec: Maps use the type name "map" and support one attribute: "values"
    // Map keys are assumed to be strings.
    String json = "{\"type\": \"map\", \"values\": \"string\"}";
    Schema schema = parser.parse(json);
    assertNotNull(schema);
    assertEquals(Schema.Type.MAP, schema.getType());
    assertEquals(Schema.Type.STRING, schema.getValueType().getType());
  }

  // Goal: Verificare che mancare l'attributo obbligatorio "items" in un array lanci eccezione
  @Test
  public void TC29_parseArrayMissingItems() {
    String json = "{\"type\": \"array\"}";
    assertThrows(SchemaParseException.class, () -> {
      parser.parse(json);
    }, "Un array deve avere l'attributo items definito");
  }


  // Goal: Verificare un Logical Type Decimal valido
  @Test
  public void TC30_parseValidLogicalTypeDecimal() {
    // Spec: "precision": positive integer, "scale": positive integer <= precision
    String json = "{" +
      "\"type\": \"bytes\", " +
      "\"logicalType\": \"decimal\", " +
      "\"precision\": 4, " +
      "\"scale\": 2" +
      "}";
    Schema schema = parser.parse(json);
    assertEquals("decimal", schema.getLogicalType().getName());
  }


  // Goal: BVA sui Logical Types.
  // La spec dice: "If a logical type is invalid... implementations should ignore the logical type"
  @Test
  public void TC31_parseInvalidDecimalScaleGreaterThanPrecision() {
    String json = "{" +
      "\"type\": \"bytes\", " +
      "\"logicalType\": \"decimal\", " +
      "\"precision\": 4, " +
      "\"scale\": 5" + // Invalid: 5 > 4
      "}";

    // Il parser NON lancia eccezione, ma ignora il logicalType.
    Schema schema = parser.parse(json);

    assertNotNull(schema);
    assertEquals(Schema.Type.BYTES, schema.getType());
    assertNull(schema.getLogicalType(), "Il logical type invalido dovrebbe essere ignorato");
  }

  // Goal: Verificare parsing di UUID (Logical type su string)
  @Test
  public void TC32_parseUUIDLogicalType() {
    String json = "{\"type\": \"string\", \"logicalType\": \"uuid\"}";
    Schema schema = parser.parse(json);
    assertEquals("uuid", schema.getLogicalType().getName());
  }


  // Goal: La specifica dice: "Default values for union fields correspond to the first schema that matches in the union."
  // Caso Positivo: Default corrisponde al primo tipo (null).
  @Test
  public void TC33_parseUnionDefaultMatchingFirstType() {
    String json = "{" +
      "\"type\": \"record\", \"name\": \"Test\", \"fields\": [" +
      "  {\"name\": \"f\", \"type\": [\"null\", \"string\"], \"default\": null}" +
      "]}";
    assertDoesNotThrow(() -> parser.parse(json));
  }

  // Goal: BVA sul default delle Union.
  // Se il default corrisponde al SECONDO tipo, deve fallire il parsing (o essere invalido).
  @Test
  public void TC34_parseUnionDefaultMismatchFirstType() {
    // Union è ["null", "string"]. Il default è "foo" (string).
    // Siccome il primo tipo è null, il default DOVREBBE essere null.
    String json = "{" +
      "\"type\": \"record\", \"name\": \"Test\", \"fields\": [" +
      "  {\"name\": \"f\", \"type\": [\"null\", \"string\"], \"default\": \"foo\"}" +
      "]}";
    assertThrows(AvroTypeException.class, () -> {
      parser.parse(json);
    }, "Il valore di default deve corrispondere al PRIMO tipo della union");
  }


  // Goal: Verificare il supporto agli Aliases (Record)
  @Test
  public void TC35_parseRecordWithAliases() {
    // Spec: aliases: a JSON array of strings
    String json = "{\"type\":\"record\", \"name\":\"NewName\", \"aliases\":[\"OldName\"], \"fields\":[]}";
    Schema schema = parser.parse(json);
    assertTrue(schema.getAliases().contains("OldName"));
  }

  // Goal: Verificare attributo 'order' nei campi record.
  // Valid values: "ascending", "descending", "ignore"
  @Test
  public void TC36_parseFieldOrderValid() {
    String json = "{\"type\":\"record\", \"name\":\"R\", \"fields\":[" +
      "{\"name\":\"f\", \"type\":\"int\", \"order\":\"descending\"}]}";
    Schema schema = parser.parse(json);
    assertEquals(Schema.Field.Order.DESCENDING, schema.getField("f").order());
  }

  // Goal: Verificare che un 'order' invalido lanci eccezione
  @Test
  public void TC37_parseFieldOrderInvalid() {
    String json = "{\"type\":\"record\", \"name\":\"R\", \"fields\":[" +
      "{\"name\":\"f\", \"type\":\"int\", \"order\":\"random\"}]}";
    assertThrows(IllegalArgumentException.class, () -> {
      parser.parse(json);
    }, "Order deve essere uno tra ascending, descending, ignore");
  }

  // Goal: Testare il metodo addTypes() documentato nelle API.
  @Test
  public void TC38_useAddTypesAndParseReference() {
    // 1. Creo un parser e parso un tipo "Common"
    Schema common = new Schema.Parser().parse("{\"type\":\"fixed\", \"name\":\"Common\", \"size\":1}");

    // 2. Creo un NUOVO parser
    Schema.Parser newParser = new Schema.Parser();

    // 3. Aggiungo il tipo "Common" al nuovo parser.
    // Utilizziamo una Map<String, Schema> invece di una List per risolvere l'errore di compilazione.
    // La chiave della mappa deve essere il nome con cui ci riferiremo al tipo (es. "Common").
    java.util.Map<String, Schema> types = new java.util.HashMap<>();
    types.put("Common", common);

    newParser.addTypes(types);

    // 4. Parso uno schema che referenzia "Common" SENZA ridefinirlo
    String json = "{\"type\":\"record\", \"name\":\"Container\", \"fields\":[{\"name\":\"c\", \"type\":\"Common\"}]}";
    Schema result = newParser.parse(json);

    assertNotNull(result);
    assertEquals("Common", result.getField("c").schema().getName());
  }

  // Goal: Testare il metodo parse(String s, String... more) per input frammentati
  @Test
  public void TC39_parseMultipleStringsVarargs() {
    // API: parse(String s, String... more)
    // Utile se il JSON è spezzato in più stringhe
    String part1 = "{\"type\": \"re";
    String part2 = "cord\", \"name\": \"Split\", \"fields\": []}";

    Schema schema = parser.parse(part1, part2);
    assertEquals("Split", schema.getName());
  }

  // Goal: Verificare il conflitto tra nome Primitivo e Nome Definito
  @Test
  public void TC40_preventPrimitiveNameRedefinition() {
    // Spec: "Primitive type names are also defined type names."
    // Non dovrei poter chiamare un record "int" o "string".
    String json = "{\"type\":\"record\", \"name\":\"int\", \"fields\":[]}";
    assertThrows(AvroTypeException.class, () -> {
      parser.parse(json);
    }, "Non si può usare un nome di tipo primitivo per un record");
  }
}
