package cn.mint.tv;
import org.junit.Test;import static org.junit.Assert.*;
public class RefreshStatusTest {
 @Test public void importedFailureDoesNotHideRegionalSuccess(){String text=RefreshStatus.summary(true,true,"福建移动",0,1);assertTrue(text.startsWith("地区频道源已更新"));assertTrue(text.contains("1 个失败"));assertTrue(text.contains("导入管理"));}
 @Test public void failedRegionalDownloadRetainsOldList(){String text=RefreshStatus.summary(true,false,"福建移动",1,0);assertTrue(text.contains("地区频道源更新失败"));assertTrue(text.contains("保留旧列表"));assertTrue(text.contains("1 个已更新"));}
 @Test public void cleanUpdateHasNoMisleadingFailure(){String text=RefreshStatus.summary(true,true,"福建移动",0,0);assertFalse(text.contains("失败"));assertFalse(text.contains("订阅"));}
 @Test public void cachedListsAreNotReportedAsDownloaded(){assertTrue(RefreshStatus.summary(false,false,"福建移动",0,0).contains("沿用已有列表"));}
}
