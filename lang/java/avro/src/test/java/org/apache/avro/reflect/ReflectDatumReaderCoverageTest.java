package org.apache.avro.reflect;


import org.apache.avro.Conversion;
import org.apache.avro.LogicalType;
import org.apache.avro.Schema;
import org.apache.avro.io.ResolvingDecoder;
import org.apache.avro.specific.SpecificData;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ReflectDatumReaderCoverageTest {

  @Mock
  private ResolvingDecoder mockDecoder;

  private ReflectDatumReader<Object> reader;

  // Classe di supporto per testare Stringable
  public static class BadStringable {
    public BadStringable(String s) {
      throw new RuntimeException("Boom!");
    }
  }

  public static class DateRecord {
    java.time.LocalDate dateField;
  }

  @Before
  public void setUp() {
    // Uso il ReflectData di default
    reader = new ReflectDatumReader<>(ReflectData.get());
  }


  //Verifico se la classe è in grado di convertire automaticamente un dato "grezzo" di avro (intero) in un oggetto
  //java complesso(LocalDate) tramite la reflection
  @Test
  public void ReadFieldWithLogicalTypeTest() throws IOException {
    // istanza ReflectData che analizza la classe DateRecord
    ReflectData data = ReflectData.get();
    // aggiungo il logical type in modo da essere sicuro che se trovo un campo che rappresenta una data usa la DateConversion
    // cioè registro il traduttore da intero a data
    data.addLogicalTypeConversion(new org.apache.avro.data.TimeConversions.DateConversion());

    //creo lo schema per DateRecord
    Schema schema = data.getSchema(DateRecord.class);
    // passo al lettore "data" che è configurato per convertire le date
    ReflectDatumReader<DateRecord> dateReader = new ReflectDatumReader<>(schema, schema, data);

    //quando il reader arriva al campo data, legge 10 e vede che c è una conversione attiva e trasforma 10 in 11-01-1970
    when(mockDecoder.readInt()).thenReturn(10);

    // eseguo la lettura, il risultato sarà un oggeto DateRecord
    DateRecord result = dateReader.read(null, mockDecoder);

    //essendo conversion!=null, siamo entrati nell'if
    assertNotNull(result.dateField);
    assertEquals(java.time.LocalDate.of(1970, 1, 11), result.dateField);
  }

  //verifico che ReflectDatumReader riesca a leggere un intero e trasformarlo in Byte
  @Test
  public void ReadIntToByteDirectlyTest() throws IOException {
    // creo uno schema che descrive un intero
    Schema byteSchema = Schema.create(Schema.Type.INT);
    //aggiungo un metadato allo schema che se arriva un int in memoria java lo istanzia come Byte
    byteSchema.addProp(SpecificData.CLASS_PROP, Byte.class.getName());

    // Simulo il decoder che legge un int con valore 10
    when(mockDecoder.readInt()).thenReturn(10);

    Object result = reader.readInt(null, byteSchema, mockDecoder);
    assertTrue("Il risultato deve essere di tipo Byte", result instanceof Byte);
    assertEquals("Il valore deve essere 10", (byte) 10, result);
  }

  //verifico che ReflectDatumReader riesca a leggere un intero e trasformarlo in Short
  @Test
  public void testReadIntToShortDirectly() throws IOException {
    //creo uno schema che rappresenta un INT
    Schema shortSchema = Schema.create(Schema.Type.INT);
    //aggiungo la proprietà che quando arriva un INT lo istanzia come Short
    shortSchema.addProp(SpecificData.CLASS_PROP, Short.class.getName());

    when(mockDecoder.readInt()).thenReturn(1000);

    Object result = reader.readInt(null, shortSchema, mockDecoder);

    assertTrue("Il risultato deve essere di tipo Short", result instanceof Short);
    assertEquals((short) 1000, result);
  }

  /**
   * Obiettivo: Verificare che readInt converta correttamente un int Avro in un Char Java.
   */
  @Test
  public void testReadIntToCharDirectly() throws IOException {
    //creo lo schema che rappresenta un INT
    Schema charSchema = Schema.create(Schema.Type.INT);
    //aggiungo la proprietà che quando arriva un INT lo istanzia come Character
    charSchema.addProp(SpecificData.CLASS_PROP, Character.class.getName());
    // 65 == ASCII per 'A'
    when(mockDecoder.readInt()).thenReturn(65);

    Object result = reader.readInt(null, charSchema, mockDecoder);

    assertTrue("Il risultato deve essere di tipo Character", result instanceof Character);
    assertEquals('A', result);
  }

  //testo il metodo newArray
  @Test
  public void testNewArrayCreatesHashSetDirectly() {
    // creo uno schema di array di stringhe
    Schema arraySchema = Schema.createArray(Schema.create(Schema.Type.STRING));
    // avro normalmente crea un GenericDataArray (ArrayList), ma qui specifico di voler una HashSet
    arraySchema.addProp(SpecificData.CLASS_PROP, HashSet.class.getName());

    // creo array di size 10 e gli passo array shema con la proprietà per HashSet
    Object result = reader.newArray(null, 10, arraySchema);

    assertNotNull(result);
    assertEquals("Deve essere istanza di HashSet", HashSet.class, result.getClass());
    assertTrue("Deve essere vuoto (newArray alloca solo)", ((HashSet<?>) result).isEmpty());
  }
  /**
   * verifico che new array crei un array list
   */
  @Test
  public void testNewArrayCreatesArrayListByDefault() {
    // creo uno schema di array di stringhe
    Schema arraySchema = Schema.createArray(Schema.create(Schema.Type.STRING));
    // non aggiungo proprietà ma metto ArrayList esplicitamente
    arraySchema.addProp(SpecificData.CLASS_PROP, ArrayList.class.getName());

    Object result = reader.newArray(null, 5, arraySchema);

    assertEquals(ArrayList.class, result.getClass());
  }

  /**
   * Testiamo direttamente readObjectArray passando uno schema che ha un LogicalType
   * e una Conversione associata.
   */
  //LLM
  @Test
  public void testReadObjectArrayWithConversion() throws Exception {
    // Creo uno schema di array bytes con LogicalType pari a "custom-logical"
    Schema elementSchema = Schema.create(Schema.Type.BYTES);
    LogicalType logicalType = new LogicalType("custom-logical");
    logicalType.addToSchema(elementSchema);
    Schema arraySchema = Schema.createArray(elementSchema);

    // Converto il reader
    ReflectData data = (ReflectData) reader.getData();
    data.addLogicalTypeConversion(new Conversion<String>() {
      @Override
      public Class<String> getConvertedType() { return String.class; }
      @Override
      public String getLogicalTypeName() { return "custom-logical"; }
      //simulo la conversione
      @Override
      public String fromBytes(java.nio.ByteBuffer value, Schema schema, LogicalType type) {
        return "CONVERTITO";
      }
    });

    // array di destinazione
    String[] targetArray = new String[1];

    // finito il primo blocco non ce ne sono altri
    when(mockDecoder.arrayNext()).thenReturn(0L);

    // Il decoder restituisce byte, la conversion li trasformerà in String
    when(mockDecoder.readBytes(any())).thenReturn(java.nio.ByteBuffer.wrap(new byte[]{1}));

    // 4. CHIAMATA VIA REFLECTION
    java.lang.reflect.Method privateMethod = ReflectDatumReader.class.getDeclaredMethod(
      "readObjectArray",
      Object[].class,
      Schema.class,
      long.class,
      ResolvingDecoder.class
    );

    privateMethod.setAccessible(true);

    // Passiamo 'elementSchema' come 'expectedType' perché readObjectArray lavora sul tipo dell'elemento
    privateMethod.invoke(reader, targetArray, elementSchema, 1L, mockDecoder);

    // 5. Verifica
    assertEquals("L'elemento deve essere stato convertito", "CONVERTITO", targetArray[0]);
  }

}
