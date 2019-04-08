/**
 * ByteArray class
 *
 * Big-endian relative put/get data into/from byte array
 * Data types: byte[], byte, short, int, long, float, double
 *
 * Usage: see test at the end of code
 *
 * Author:  miktim@mail.ru
 * Created: 2018-12-15
 * Updated: 2018-12-19
 *
 * License: MIT
 */

import java.util.Arrays;

public class ByteArray {
  private byte[] data;
  private int dataPointer = 0; 
  private int dataLength = 0;
  private boolean dataResizable = true;
  
  public static final int AUTOEXPAND_INCREMENT = 64;

  public ByteArray() {
    data = new byte[AUTOEXPAND_INCREMENT];
  }

  public ByteArray(byte[] bytes) throws NullPointerException {
    data = bytes;
    dataLength = data.length;
    dataResizable = false;
  }
  
  public byte[] array() {
    if (dataLength == data.length) return data;
    return Arrays.copyOf(data, dataLength);
  }

  public int truncate() throws IllegalStateException {
    if(!dataResizable) throw new IllegalStateException(); //??? suitable java exception
    dataLength = dataPointer;
    return dataLength;
  }
   
  private int checkPointer(int pointer) throws ArrayIndexOutOfBoundsException {
    if (pointer < 0 || pointer > dataLength) 
      throw new ArrayIndexOutOfBoundsException();
    return pointer;
  }
  
  private int checkLength(int newPointer) throws ArrayIndexOutOfBoundsException {
    if (!dataResizable) checkPointer(newPointer);
    if (newPointer > data.length) {
      data = Arrays.copyOf(data, newPointer + AUTOEXPAND_INCREMENT);
    }
    if (dataLength < newPointer) dataLength = newPointer; 
    return newPointer;
  }
   
  public void resizable(boolean onoff) {
    dataResizable = onoff;
  }
  
  boolean isResizable() {
    return dataResizable;
  }

  public int length() {
    return dataLength;
  }

  public int getPointer() {
    return dataPointer;
  }
  
  public void setPointer(int newPointer) throws ArrayIndexOutOfBoundsException {
    dataPointer = checkPointer(newPointer);
  }
  
/**
 * PUT data methods. All methods return а new pointer.
 */
  public int put(byte[] bytes) throws ArrayIndexOutOfBoundsException, NullPointerException {
    int newPointer = checkLength(dataPointer + bytes.length);
    System.arraycopy(bytes, 0, data, dataPointer, bytes.length);
    dataPointer = newPointer;
//    checkLength(dataPointer + bytes.length);
//    for(int i = 0; i < bytes.length; i++) data(dataPointer++) = bytes[i];
    return dataPointer;
  }
  
  public int put(byte b) throws ArrayIndexOutOfBoundsException {
    checkLength(dataPointer + 1);
    data[dataPointer++] = b;
    return dataPointer;
  }
  
  public int putShort(short s) throws ArrayIndexOutOfBoundsException {
    checkLength(dataPointer + 2);
    data[dataPointer++] = (byte) (s >>> 8);
    data[dataPointer++] = (byte) s;
    return dataPointer;
  }

  public int putInt(int i) throws ArrayIndexOutOfBoundsException {
    checkLength(dataPointer + 4);
    data[dataPointer++] = (byte)(i >>> 24);
    data[dataPointer++] = (byte)(i >>> 16);
    data[dataPointer++] = (byte)(i >>> 8);
    data[dataPointer++] = (byte) i ;
    return dataPointer;
  }
// IEE754
  public int putFloat(float f) throws ArrayIndexOutOfBoundsException {
//      return putInt(java.lang.Float.floatToIntBits(f)); 
    return putInt(java.lang.Float.floatToRawIntBits(f)); // preserving NaN
  }
  
  public int putLong(long i) throws ArrayIndexOutOfBoundsException {
    checkLength(dataPointer + 8);
    data[dataPointer++] = (byte)(i >>> 56);
    data[dataPointer++] = (byte)(i >>> 48);
    data[dataPointer++] = (byte)(i >>> 40);
    data[dataPointer++] = (byte)(i >>> 32);
    data[dataPointer++] = (byte)(i >>> 24);
    data[dataPointer++] = (byte)(i >>> 16);
    data[dataPointer++] = (byte)(i >>> 8);
    data[dataPointer++] = (byte) i;
    return dataPointer;
  }
// IEE754 
  public int putDouble(double d) throws ArrayIndexOutOfBoundsException {
//    return putLong(java.lang.Double.doubleToLongBits(d)); 
    return putLong(java.lang.Double.doubleToRawLongBits(d)); //preserving NaN
  }
/**
 * GET data methods.
 */
  public byte[] get(int len) 
          throws ArrayIndexOutOfBoundsException, IllegalArgumentException {
    if (len < 0) throw new IllegalArgumentException();
    int oldPointer = dataPointer;
    dataPointer = checkPointer(dataPointer + len);
    return Arrays.copyOfRange(data, oldPointer, dataPointer);
  }
  
  public byte get() throws ArrayIndexOutOfBoundsException {
    checkPointer(dataPointer + 1) ;
    return data[dataPointer++];
  }
  
  public short getShort() throws ArrayIndexOutOfBoundsException {
    checkPointer(dataPointer + 2);
    return (short)((data[dataPointer++] << 8) + (data[dataPointer++] & 0xff));
  }

  public int getInt() throws ArrayIndexOutOfBoundsException {
    checkPointer(dataPointer + 4);
    return (data[dataPointer++] << 24)
	  + ((data[dataPointer++] & 0xff) << 16)
	  + ((data[dataPointer++] & 0xff) << 8)
	  + (data[dataPointer++]  & 0xff);
  }
  
  public float getFloat() throws ArrayIndexOutOfBoundsException {
    return java.lang.Float.intBitsToFloat(getInt());
  }
  
  public long getLong() throws ArrayIndexOutOfBoundsException {
    checkPointer(dataPointer + 8);
    return ((long)data[dataPointer++] << 56)
	  + ((long)(data[dataPointer++] & 0xff) << 48)
	  + ((long)(data[dataPointer++] & 0xff) << 40)
	  + ((long)(data[dataPointer++] & 0xff) << 32)
	  + ((long)(data[dataPointer++] & 0xff) << 24)
	  + (long)((data[dataPointer++] & 0xff) << 16)
	  + (long)((data[dataPointer++] & 0xff) << 8)
	  + (long)(data[dataPointer++] & 0xff);
  }

  public double getDouble() throws ArrayIndexOutOfBoundsException {
    return java.lang.Double.longBitsToDouble(getLong());
  }
  
/* Simple test.
  public static void main(String[] args) throws Exception {
    String title = "ByteArray test";
    byte b = (byte) 12;
    short s = (short) 0xf0f0;
    int i = 0xf0f0f0f0;
    long l = 0xf0f0f0f0f0f0f0f0L;
    byte[] t = title.getBytes();
    float f = 3.1416f;
    double d = (double) f;
    
    System.out.println(title + "\r\nPut data...");
    ByteArray ba = new ByteArray();
    
    ba.put(b);
    ba.putShort(s);
    ba.putShort((short)~s);
    ba.putInt(i);
    ba.putInt(~i);
    ba.put(t);
    ba.putLong(l);
    ba.putLong(~l);
    ba.putFloat(f);
    ba.putFloat(-f);
    ba.putDouble(d);
    int p = ba.putDouble(-d);
    
    int expectedLength = 1+2+2+4+4+t.length+8+8+4+4+8+8;
    if (ba.length() != expectedLength) System.out.println("  Expected length FAIL!");
    
    while(ba.length() < ByteArray.AUTOEXPAND_INCREMENT) ba.put(ba.array());
    
    if(ba.getPointer() != ba.length()) System.out.println("  Resizable enabled FAIL!");
    ba.setPointer(p);
    ba.truncate();
    ba.resizable(false);
    try { 
      ba.put(b);
      System.out.println("  Resizable disabled FAIL!");
    } catch (Exception e) {}
    
    System.out.println("Get data...");
    ba = new ByteArray(ba.array());
    
    ba.get(0);
    ba.put(new byte[0]);
    if (ba.get() != b) System.out.println("  Byte put/get FAIL!");
    if (ba.getShort() != s) System.out.println("  Short1 put/get FAIL!");
    if (ba.getShort() != ~s) System.out.println("  Short2 put/get FAIL!");
    if (ba.getInt() != i) System.out.println("  Int1 put/get FAIL!");
    if (ba.getInt() != ~i) System.out.println("  Int2 put/get FAIL!");
    if (!Arrays.equals(ba.get(t.length),t)) System.out.println("  Byte[] put/get FAIL!");
    if (ba.getLong() != l) System.out.println("  Long1 put/get FAIL!");
    if (ba.getLong() != ~l) System.out.println("  Long2 put/get FAIL!");
    if (ba.getFloat() != f) System.out.println("  Float1 put/get FAIL!");
    if (ba.getFloat() != -f) System.out.println("  Float2 put/get FAIL!");
    if (ba.getDouble() != d) System.out.println("  Double1 put/get FAIL!");
    if (ba.getDouble() != -d) System.out.println("  Double2 put/get FAIL!");
    try { 
      ba.get(1);
      System.out.println("  Get after data end FAIL!");
    } catch (Exception e) {}
    System.out.println("Test completed");
  }
*/
}
