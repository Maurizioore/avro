package org.apache.avro.reflect;

import org.apache.avro.Schema;
import org.apache.avro.io.ResolvingDecoder;
import org.apache.avro.util.Utf8;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ReflectDatumReaderMutantTest {

  @Mock
  private ResolvingDecoder mockDecoder;

  private ReflectDatumReader<Object> reader;
  private ReflectData reflectData;

  @Before
  public void setUp() {
    reflectData = ReflectData.get();
    reader = new ReflectDatumReader<>(reflectData);
  }


  public static class CollectionsPojo {
    List<String> listField;
    Set<String> setField;
    Map<Integer, String> complexMap; // Map con chiave non string
  }

  public static class BytePojo {
    byte[] data;
  }

  public static class OptionalPojo {
    Optional<String> maybeString;
  }

  public static class StringablePojo {
    @Stringable
    java.math.BigInteger value; // BigInteger è Stringable di default
  }

  public static class NoConversionPojo {
    String[] textArray;
  }

  // Il reader deve capire che deve estrarre il contenuto dal byteBuffer e iniettarlo in un byte[] per metterlo in result
  // kill mutante 245-248
  @Test
  public void testReadBytesToNativeArray() throws IOException {
    // creo lo schema tramite reflection della classe BytePojo
    Schema schema = reflectData.getSchema(BytePojo.class);
    // dico al reader che tipo di schema si deve aspettare
    reader.setSchema(schema);

    // simulo la lettura di 3 byte
    byte[] inputBytes = new byte[] { 0xA, 0xB, 0xC };
    //preparo il dato che il decoder restituirà
    ByteBuffer buffer = ByteBuffer.wrap(inputBytes);
    when(mockDecoder.readBytes(any())).thenReturn(buffer);
    //effettuo la lettura
    BytePojo result = (BytePojo) reader.read(null, mockDecoder);
    assertNotNull(result.data);
    assertArrayEquals("Deve convertire ByteBuffer in byte[] nativo", inputBytes, result.data);
  }

  // caso se la mappa ha chiavi non string riga 150
  @Test
  public void testReadMapWithNonStringKeys() throws IOException {
    // creo lo scheman
    Schema schema = reflectData.getSchema(CollectionsPojo.class);
    // estraggo lo schema della mappa con chiave non string
    Schema mapSchema = schema.getField("complexMap").schema();
    ReflectDatumReader<Map<Integer, String>> mapReader = new ReflectDatumReader<>(mapSchema);
    // simulo la lettura di una mappa con un singolo elemento
    when(mockDecoder.readArrayStart()).thenReturn(1L);
    // fine dopo il primo blocco
    when(mockDecoder.arrayNext()).thenReturn(0L);

    // simulo la lettura della chiave e del valore (intero e stringa)
    when(mockDecoder.readInt()).thenReturn(100); // Key
    when(mockDecoder.readString(any())).thenReturn(new Utf8("Valore100")); // Value

    Map<Integer, String> result = mapReader.read(null, mockDecoder);

    assertNotNull(result);
    assertEquals("La mappa deve contenere 1 elemento", 1, result.size());
    assertTrue("La chiave 100 deve esistere", result.containsKey(100));
    assertEquals("Il valore deve corrispondere", "Valore100", result.get(100));
  }

  // Verifico che se passo una lista vecchia essa venga svuotata e la riutilizza, mutante 93
  @Test
  public void testCollectionReuseAndClear() throws IOException {
    // creo lo schema della lista
    Schema listSchema = reflectData.getSchema(CollectionsPojo.class).getField("listField").schema();
    // preparo il reader per liste di stringhe
    ReflectDatumReader<List<String>> listReader = new ReflectDatumReader<>(listSchema);

    // preparo una lista vecchia con dati
    List<String> oldList = new ArrayList<>();
    oldList.add("SPORCIZIA");

    //array vuoto, cosi che venga ripulito e non aggiunto nulla
    when(mockDecoder.readArrayStart()).thenReturn(0L);

    List<String> result = listReader.read(oldList, mockDecoder);

    // verifico che non sia stata creata una nuova lista
    assertSame("Deve restituire la stessa istanza", oldList, result);
    assertTrue("La lista deve essere stata svuotata", result.isEmpty());
  }

  // verifico che gli array nativi senza logiche di conversione vengano gestiti con un passaggio diretto 199-204
  @Test
  public void testReadObjectArrayWithoutConversion() throws IOException {
    // creo lo schema dell'array di stringhe
    Schema schema = reflectData.getSchema(NoConversionPojo.class).getField("textArray").schema();
    ReflectDatumReader<String[]> arrayReader = new ReflectDatumReader<>(schema);


    when(mockDecoder.readArrayStart()).thenReturn(2L);
    when(mockDecoder.arrayNext()).thenReturn(0L);
    // alla prima chiamata ritorna "A", alla seconda "B"
    when(mockDecoder.readString(any())).thenReturn(new Utf8("A")).thenReturn(new Utf8("B"));

    String[] result = arrayReader.read(null, mockDecoder);

    assertNotNull(result);
    assertEquals(2, result.length);
    assertEquals("A", result[0]);
    assertEquals("B", result[1]);
  }

  // verifico che Optional venga gestito correttamente 301-305
  @Test
  public void testReadOptionalField() throws IOException {
    // avro modella Optional<String> come Union [null, string]
    Schema schema = reflectData.getSchema(OptionalPojo.class);
    reader.setSchema(schema);
    // dico che arriva una stringa
    when(mockDecoder.readIndex()).thenReturn(1);
    when(mockDecoder.readString(any())).thenReturn(new Utf8("Presente"));

    OptionalPojo result = (OptionalPojo) reader.read(null, mockDecoder);

    assertNotNull(result.maybeString);
    assertTrue(result.maybeString.isPresent());
    assertEquals("Presente", result.maybeString.get());
  }



  // Verifico che il sistema non fallisca quando incontra un array vuoto
  @Test
  public void testReadEmptyArray() throws IOException {
    // creo lo schema dell'array di stringhe
    Schema schema = reflectData.getSchema(NoConversionPojo.class).getField("textArray").schema();
    ReflectDatumReader<String[]> arrayReader = new ReflectDatumReader<>(schema);
    //array con 0 elementi
    when(mockDecoder.readArrayStart()).thenReturn(0L);
    //provo a leggere
    String[] result = arrayReader.read(null, mockDecoder);
    //verifico che il risultato sia un array di lunghezza 0 e non null
    assertNotNull(result);
    assertEquals(0, result.length);
  }

  // ========================= Generated LLM ====================================================
  // Copertura Linee: 282 (Ternary operator: asString == null ? null ...)
  // Obiettivo: Verificare che se la stringa letta è null, setti null invece di esplodere
  // ===================================================================================
  @Test
  public void testReadStringableNull() throws IOException {
    Schema schema = reflectData.getSchema(StringablePojo.class);

    // Uso classe anonima per accedere al metodo protected read e sovrascriverlo
    ReflectDatumReader<StringablePojo> nullReturningReader = new ReflectDatumReader<StringablePojo>(
      StringablePojo.class) {
      @Override
      protected Object read(Object old, Schema expected, ResolvingDecoder in) throws IOException {
        if (expected.getType() == Schema.Type.STRING) {
          return null;
        }
        try {
          return super.read(old, expected, in);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    };

    nullReturningReader.setSchema(schema);

    StringablePojo record = new StringablePojo();
    Schema.Field field = schema.getField("value");

    // Chiamata a readField
    nullReturningReader.readField(record, field, null, mockDecoder, null);

    assertNull("Il campo value deve essere null", record.value);
  }
}
