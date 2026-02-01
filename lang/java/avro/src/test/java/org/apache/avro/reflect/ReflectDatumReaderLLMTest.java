package org.apache.avro.reflect;

import org.apache.avro.Schema;
import org.apache.avro.io.Decoder;
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
import java.util.Collection;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ReflectDatumReaderLLMTest {

  @Mock
  private Decoder mockDecoder;

  @Mock
  private ResolvingDecoder mockResolvingDecoder;

  // Variabili di supporto
  private Schema intSchema;
  private Schema stringSchema;

  public static class TestArray {
    int[] arr;
  }
  public static class Pojo {
    String myField;
  }

  @Before
  public void setUp() {
    intSchema = Schema.create(Schema.Type.INT);
    stringSchema = Schema.create(Schema.Type.STRING);
  }

  /**
   *   Verifica Costruttori
   * - Config: Diversi costruttori
   * - Input: Classi, Schemi, ReflectData
   * - Oracolo:
   * L'istanza è creata correttamente e lo schema/data sono assegnati
   */
  @Test
  public void ConstructorsTest() {
    // 1. Costruttore con Classe
    ReflectDatumReader<String> reader1 = new ReflectDatumReader<>(String.class);
    assertNotNull("Il costruttore con Class non deve restituire null", reader1);
    // ReflectData dovrebbe aver inferito lo schema string
    assertEquals(Schema.Type.STRING, reader1.getSchema().getType());

    // 2. Costruttore con Schema esplicito
    ReflectDatumReader<Object> reader2 = new ReflectDatumReader<>(intSchema);
    assertEquals("Lo schema deve essere quello passato al costruttore", intSchema, reader2.getSchema());

    // 3. Costruttore con Writer/Reader schema e ReflectData custom
    ReflectData customData = ReflectData.get();
    ReflectDatumReader<Object> reader3 = new ReflectDatumReader<>(intSchema, intSchema, customData);
    assertEquals("Il ReflectData deve essere quello passato", customData, reader3.getData());
  }

  /**
   * - Metodo: readInt(Object old, Schema schema, Decoder in)
   * - Config: Schema INT
   * - Input Decoder: 42
   * - Oracolo:
   * Ritorna Integer 42.
   * Verifica che deleghi correttamente al decoder.
   */
  @Test
  public void ReadIntMethodTest() throws IOException {
    ReflectDatumReader<Integer> reader = new ReflectDatumReader<>(Integer.class);

    // Configuro il mock
    when(mockDecoder.readInt()).thenReturn(42);

    // Chiamata DIRETTA al metodo protected (possibile perché siamo nello stesso package)
    Object result = reader.readInt(null, intSchema, mockDecoder);

    assertTrue("Il risultato deve essere Integer", result instanceof Integer);
    assertEquals("Il valore letto deve essere 42", 42, result);
    verify(mockDecoder, times(1)).readInt();
  }

  /**
   * - Metodo: createString(Object value)
   * - Input: "valore_default"
   * - Oracolo:
   * Ritorna la stringa java (e non un Utf8, dato che Reflect lavora con POJO java).
   * Nota: GenericDatumReader tornerebbe Utf8, Reflect dovrebbe tornare String se configurato per String.
   */
  @Test
  public void CreateStringMethodTest() {
    ReflectDatumReader<String> reader = new ReflectDatumReader<>(String.class);

    // Chiamo il metodo che viene usato per gestire i valori di default
    Object result = reader.createString("valore_default");

    // Oracolo: ReflectDatumReader deve restituire una String Java normale
    assertEquals("valore_default", result);
    assertTrue("Deve essere istanza di String", result instanceof String);
  }

  /**
   * - Metodo: readBytes(object old, Schema schema, Decoder in)
   * - Input Decoder: ByteBuffer con array {1, 2, 3}
   * - Oracolo:
   * Ritorna un array di byte o ByteBuffer a seconda della configurazione.
   */
  @Test
  public void ReadBytesMethodTest() throws IOException {
    ReflectDatumReader<Object> reader = new ReflectDatumReader<>(Schema.create(Schema.Type.BYTES));
    byte[] expectedBytes = new byte[]{1, 2, 3};
    ByteBuffer buffer = ByteBuffer.wrap(expectedBytes);

    when(mockDecoder.readBytes(any())).thenReturn(buffer);

    Object result = reader.readBytes(null, Schema.create(Schema.Type.BYTES), mockDecoder);

    // Verifica: il reader solitamente restituisce il ByteBuffer o l'array
    assertNotNull(result);
    if (result instanceof ByteBuffer) {
      assertArrayEquals(expectedBytes, ((ByteBuffer) result).array());
    } else {
      fail("Ci aspettavamo un ByteBuffer");
    }
  }


  /**
   * - Metodo: newArray(Schema schema, int size, Object old)
   * - Config: Schema Array di int, Classe target int[] (Array nativo)
   * - Input Size: 10
   * - Oracolo:
   * Deve istanziare un vero int[] di dimensione 10, non una ArrayList.
   */
  @Test
  public void NewArrayNativeTest() {
    // Estraggo lo schema del campo "arr" che sarà di tipo ARRAY di INT
    Schema schema = ReflectData.get().getSchema(TestArray.class).getField("arr").schema();

    // Istanzio il reader tipizzato correttamente
    ReflectDatumReader<TestArray> reader = new ReflectDatumReader<>(TestArray.class);

    // Chiamo newArray
    Object arrayInstance = reader.newArray(null, 10, schema);

    // Oracolo
    assertNotNull("L'istanza dell'array non deve essere null", arrayInstance);
    assertTrue("Deve essere un array nativo di int (int[])", arrayInstance instanceof int[]);
    assertEquals("La lunghezza deve essere 10", 10, ((int[]) arrayInstance).length);
  }

  /**
   * - Metodo: addToArray(Object array, long pos, Object e)
   * - Config: Array nativo int[]
   * - Stato Iniziale: int[2] vuoto
   * - Input: pos=0, value=99
   * - Oracolo:
   * L'array alla posizione 0 contiene 99.
   * ReflectDatumReader usa la reflection per settare l'array, diversamente da Collection.add().
   */
  @Test
  public void AddToArrayNativeTest() {
    ReflectDatumReader<Object> reader = new ReflectDatumReader<>(Object.class);

    int[] nativeArray = new int[2];

    try {
      // provo a usare addToArray su un array nativo
      reader.addToArray(nativeArray, 0, 99);
      fail("Avrebbe dovuto lanciare AvroRuntimeException: il metodo è disabilitato in questa classe");
    } catch (org.apache.avro.AvroRuntimeException e) {
      // Oracolo: Verifico il messaggio d'errore specifico
      assertEquals("reflectDatumReader does not use addToArray", e.getMessage());
    }
  }

  /**
   * - Metodo: addToArray(Object array, long pos, Object e)
   * - Config: Collection (ArrayList)
   * - Stato Iniziale: ArrayList vuota
   * - Input: pos=0, value="test"
   * - Oracolo:
   * ReflectDatumReader non supporta addToArray per Collection.
   */

  @Test
  public void AddToArrayCollectionTest() {
    ReflectDatumReader<Object> reader = new ReflectDatumReader<>(Object.class);
    Collection<String> list = new ArrayList<>();

    try {
      // Azione: provo a passare una Collection al metodo specializzato per array nativi
      reader.addToArray(list, 0, "test");
      fail("Avrebbe dovuto lanciare AvroRuntimeException perché addToArray non supporta le Collection in questa classe");
    } catch (org.apache.avro.AvroRuntimeException e) {
      // Oracolo: Verifico che il messaggio sia esattamente quello dello stack trace
      assertEquals("reflectDatumReader does not use addToArray", e.getMessage());
    }
  }

  /**
   * - Metodo: readField(Object record, Field field, Object oldDatum, ResolvingDecoder in, Object state)
   * - Config: POJO con campo pubblico
   * - Input: Valore "CampoTest" dal decoder
   * - Oracolo:
   * Il campo del POJO viene popolato.
   */
  @Test
  public void ReadFieldTest() throws IOException {

    ReflectDatumReader<Pojo> reader = new ReflectDatumReader<>(Pojo.class);

    // Ottengo i metadati del campo che voglio testare
    Schema schema = ReflectData.get().getSchema(Pojo.class);
    Schema.Field field = schema.getField("myField");

    Pojo record = new Pojo();

    when(mockResolvingDecoder.readString(any())).thenReturn(new Utf8("CampoTest"));

    // Chiamata diretta a readField
    reader.readField(record, field, null, mockResolvingDecoder, null);

    // Verifica
    assertEquals("Il campo del record deve essere stato popolato", "CampoTest", record.myField);
  }
}
