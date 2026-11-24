package cn.mint.tv;
import org.junit.Test;import static org.junit.Assert.*;import java.util.*;import com.google.zxing.*;import com.google.zxing.common.*;import com.google.zxing.qrcode.*;
public class ImportQrTest {
 @Test public void phoneCanDecodeSessionAddress(){try{String address="http://192.168.100.236:43210/0123456789abcdef0123456789abcdef/";BitMatrix matrix=new QRCodeWriter().encode(address,BarcodeFormat.QR_CODE,600,600,Collections.singletonMap(EncodeHintType.MARGIN,3));int[] pixels=new int[600*600];for(int y=0;y<600;y++)for(int x=0;x<600;x++)pixels[y*600+x]=matrix.get(x,y)?0xff101c22:0xffffffff;Result result=new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(600,600,pixels))));assertEquals(address,result.getText());}catch(Exception e){throw new AssertionError(e);}}
}
