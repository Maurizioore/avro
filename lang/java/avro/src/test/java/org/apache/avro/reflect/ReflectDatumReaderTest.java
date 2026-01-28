package org.apache.avro.reflect;

import org.apache.avro.AvroTypeException;
import org.apache.avro.Schema;
import org.apache.avro.io.Decoder;
import org.apache.avro.util.Utf8;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ReflectDatumReaderTest {

  // Mock del decoder
  @Mock
  private Decoder mockDecoder;

  public static class Utente {
    String nome;
    int eta;
  }

  public static class UtenteEvoluto {
    String nome;
    int eta;
    String nazionalita;
  }

  public static class ArrayContainer {
    int[] numeri;
  }

  // TC01 verifico che avviene la conversione da utf8 a stringa
  @Test
  public void ConversionStringTest() throws IOException {
    // preparo lo schema
    Schema schema = Schema.create(Schema.Type.STRING);
    // l obiettivo è leggere una stringa e quindi preparo un lettore di stringhe
    ReflectDatumReader<String> reader = new ReflectDatumReader<>(String.class);
    // dico al reader che tipo di schema si deve aspettare
    reader.setSchema(schema);

    // simulo il comportamento del decoder
    when(mockDecoder.readString(any())).thenReturn(new Utf8("TestString"));

    String result = reader.read(null, mockDecoder);

    assertNotNull("Il risultato non deve essere null", result);
    //verifico che il ReflectDatumReade abbia convertio da formato Utf8 a String
    assertEquals("Il valore letto non corrisponde a quello atteso! Risposta assert:", "TestString", result);
  }

  /**
   TC02 verifico che l utente venga creato correttamente
   */
  @Test
  public void ReadPojoTest() throws IOException   {
    // invece di scrivere il Json della classe Utente lo genero
    Schema schemaUtente = ReflectData.get().getSchema(Utente.class);
    // preparo il lettore di classe Utente
    ReflectDatumReader<Utente> reader = new ReflectDatumReader<>(Utente.class);
    // dico al reader che tipo di schema si deve aspettare
    reader.setSchema(schemaUtente);

    // configuro i mock
    when(mockDecoder.readString(any())).thenReturn(new Utf8("Mario"));
    when(mockDecoder.readInt()).thenReturn(30);

    // eseguo
    Utente result = reader.read(null, mockDecoder);

    // verifico che l utente sia stato creato e popolato correttamente
    assertEquals("Il campo nome non è stato popolato correttamente tramite reflection", "Mario", result.nome);
    assertEquals("Il campo eta non è stato popolato correttamente", 30, result.eta);
  }

  /**
   TC03 verifico che int venga promosso a long
   */
  @Test
  public void PrimitiveTypePromotionTest() throws IOException {
    // 2 schemi
    // writer che scrive int
    Schema intSchema = Schema.create(Schema.Type.INT);
    // reader che si aspetta long
    Schema longSchema = Schema.create(Schema.Type.LONG);

    // inizializzo il reader
    ReflectDatumReader<Object> reader = new ReflectDatumReader<>(intSchema, longSchema);

    // Il decoder legge fisicamente un int
    when(mockDecoder.readInt()).thenReturn(100);

    Object result = reader.read(null, mockDecoder);

    assertTrue("Il risultato deve essere di tipo Long a causa della promozione", result instanceof Long);
    assertEquals("Il valore numerico deve essere preservato", 100L, result);
  }

  /**
   TC04 Verifica schema evolution
   */
  @Test
  public void SchemaEvolutionFieldAdditionTest() throws IOException {

    // schema writer generato dalla classe base (solo nome, eta)
    Schema writerSchema = ReflectData.get().getSchema(Utente.class);

    // costruisco un JSON che rappresenta la classe UtenteEvoluto con il default
    String schemaWithDefault = "{"
      + "\"type\":\"record\","
      + "\"name\":\"UtenteEvoluto\","
      + "\"namespace\":\"org.apache.avro.reflect.ReflectDatumReaderTest\","
      + "\"fields\":["
      + "  {\"name\":\"nome\", \"type\":\"string\"},"
      + "  {\"name\":\"eta\", \"type\":\"int\"},"
      + "  {\"name\":\"nazionalita\", \"type\":\"string\", \"default\":\"Italia\"}"
      + "]}";

    // schema reader
    Schema readerSchema = new Schema.Parser().parse(schemaWithDefault);

    // leggerò un UtenteEvoluto
    ReflectDatumReader<UtenteEvoluto> reader = new ReflectDatumReader<>(UtenteEvoluto.class);
    // imposto lo schema con cui sono stati scritti i dati
    reader.setSchema(writerSchema);
    // imposto lo schema che ci aspettiamo
    reader.setExpected(readerSchema);

    when(mockDecoder.readString(any())).thenReturn(new Utf8("Luigi"));
    when(mockDecoder.readInt()).thenReturn(40);

    UtenteEvoluto result = reader.read(null, mockDecoder);

    assertEquals("Il campo nome deve essere letto dallo stream", "Luigi", result.nome);
    assertEquals("Il campo eta deve essere letto dallo stream", 40, result.eta);
    assertEquals("Il campo nazionalita deve assumere il valore di default", "Italia", result.nazionalita);
  }

  /**
   TC05 Verifica gestione array nativi
   */
  @Test
  public void NativeArrayHandlingTest() throws IOException {
    // creo schema array
    Schema schemaArray = ReflectData.get().getSchema(ArrayContainer.class);

    // istanzio il reader per ArrayContainer
    ReflectDatumReader<ArrayContainer> reader = new ReflectDatumReader<>(ArrayContainer.class);
    reader.setSchema(schemaArray);

    // simulazione lettura array
    when(mockDecoder.readArrayStart()).thenReturn(2L);
    //array di 2 elementi 10,20
    when(mockDecoder.readInt()).thenReturn(10).thenReturn(20);
    // non ci sono altri blocchi
    when(mockDecoder.arrayNext()).thenReturn(0L);

    ArrayContainer result = reader.read(null, mockDecoder);
    // Verifico che sia stato instanziato un vedp
    assertNotNull("L'array interno 'numeri' non deve essere null", result.numeri);
    assertEquals("La lunghezza dell'array deve essere 2", 2, result.numeri.length);
    assertEquals("Il primo elemento deve essere 10", 10, result.numeri[0]);
  }

  /**
   * TC06 Verifica field removal
   * writer Schema (UtenteEvoluto) reader Schema (Utente)
   */
  @Test
  public void FieldRemovalTest() throws IOException {
    // schema writer ha nome, eta, descrizione
    Schema writerSchema = ReflectData.get().getSchema(UtenteEvoluto.class);
    // schema reader ha solo nome, eta
    Schema readerSchema = ReflectData.get().getSchema(Utente.class);

    // configurazione Reader
    ReflectDatumReader<Utente> reader = new ReflectDatumReader<>(Utente.class);
    // i dati arrivano in formato UtenteEvoluto
    reader.setSchema(writerSchema);
    // noi vogliamo questo oggetto finale
    reader.setExpected(readerSchema);
    //non configuro il ritorno per la descrizione perche dovrà essere skippata dato che non appartiene a Utente
    when(mockDecoder.readString(any())).thenReturn(new Utf8("Mario"));
    when(mockDecoder.readInt()).thenReturn(50);

    Utente result = reader.read(null, mockDecoder);

    assertEquals("Il nome deve essere letto correttamente", "Mario", result.nome);
    assertEquals("L'età deve essere letta correttamente", 50, result.eta);
    //verifico che è stata skippata la descrizione
    verify(mockDecoder, times(1)).skipString();
  }

  /**
   * TC07 Verifico rilevamento incompatibilità tra reader e writer
   */
  @Test
  public void IncompatibleSchemaTest() throws IOException {
    Schema writer = Schema.create(Schema.Type.STRING);
    Schema reader = Schema.create(Schema.Type.INT);

    try {
      // qui uso genericamente Object o Void, l'importante è che fallisca la risoluzione
      ReflectDatumReader<Object> readerObj = new ReflectDatumReader<>(writer, reader);
      readerObj.read(null, mockDecoder);
      fail("Avrebbe dovuto lanciare AvroTypeException per schemi incompatibili");
    } catch (AvroTypeException e) {
      assertNotNull("L'eccezione deve esistere", e);
    }
  }

  /**
   * TC08 Verifico gestione union
   */
  @Test
  public void ReadNullFromUnionTest() throws IOException {
    // creo uno schema union esplicito null o string
    List<Schema> types = Arrays.asList(Schema.create(Schema.Type.NULL), Schema.create(Schema.Type.STRING));
    Schema unionSchema = Schema.createUnion(types);

    // uso <Object> perché la union può ritornare tipi misti (null o string)
    ReflectDatumReader<Object> reader = new ReflectDatumReader<>(unionSchema);

    // simulo che il decoder legga l'indice 0 della union (che è null)
    when(mockDecoder.readIndex()).thenReturn(0);

    Object result = reader.read(null, mockDecoder);
    assertNull("Se l'indice della union punta a Null, il risultato deve essere null", result);
  }

  /**
   * TC09 Verifico robustezza contro dati corrotti
   */
  @Test
  public void DataCorruptionTest() throws IOException {
    ReflectDatumReader<Integer> reader = new ReflectDatumReader<>(Integer.class);
    reader.setSchema(Schema.create(Schema.Type.INT));

    // simulo errore
    when(mockDecoder.readInt()).thenThrow(new IOException("Stream troncato"));

    try {
      reader.read(null, mockDecoder);
      fail("fallimento");
    } catch (IOException e) {
      assertEquals("Il messaggio dell'eccezione deve corrispondere", "Stream troncato", e.getMessage());
    }
  }


}
