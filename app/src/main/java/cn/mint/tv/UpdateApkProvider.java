package cn.mint.tv;
import android.content.*;import android.database.*;import android.net.Uri;import android.os.*;import android.provider.OpenableColumns;import java.io.*;
/** Read-only, single-file installer grant. No external paths, directory access or writes. */
public final class UpdateApkProvider extends ContentProvider {
 public boolean onCreate(){return true;}
 File file(Uri uri)throws FileNotFoundException{if(!"/apk".equals(uri.getPath())||uri.getQuery()!=null)throw new FileNotFoundException();File f=new File(getContext().getCacheDir(),"updater/update.apk");if(!f.isFile())throw new FileNotFoundException();return f;}
 public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{if(!"r".equals(mode))throw new FileNotFoundException();return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);}
 public String getType(Uri uri){return "application/vnd.android.package-archive";}
 public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){try{File f=file(uri);String[] cols=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor cursor=new MatrixCursor(cols);Object[] values=new Object[cols.length];for(int i=0;i<cols.length;i++)values[i]=OpenableColumns.DISPLAY_NAME.equals(cols[i])?"mint-tv-update.apk":OpenableColumns.SIZE.equals(cols[i])?f.length():null;cursor.addRow(values);return cursor;}catch(FileNotFoundException e){return null;}}
 public Uri insert(Uri u,ContentValues v){throw new SecurityException();}public int update(Uri u,ContentValues v,String s,String[] a){throw new SecurityException();}public int delete(Uri u,String s,String[] a){throw new SecurityException();}
}
