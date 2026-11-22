import cn.mint.tv.NetworkProfile;
public class NetworkProfileTest {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static NetworkProfile profile(String country,String region,String isp){return NetworkProfile.detect(country,region,isp);}
    public static void main(String[] args){
        var f=profile("中国","福建省","中国移动");
        check(f!=null&&f.key().equals("fujian/mobile"),"Chinese region and carrier");
        check(f.url().endsWith("Unicast/fujian/mobile.txt"),"regional repository path");
        check(f.sameNetwork(profile("CN","Fujian","China Mobile Communications Group")),"same region/carrier retained across providers or IP changes");
        check(!f.sameNetwork(profile("CN","Fujian","China Telecom")),"carrier change requires new source");
        check(!f.sameNetwork(profile("CN","Zhejiang","China Mobile")),"province change requires new source");
        check(profile("CN","Shaanxi","CHINANET").province.equals("shaanxi"),"Shaanxi normalized");
        check(profile("CN","Shanxi","China Unicom").province.equals("shanxi"),"Shanxi remains distinct");
        check(profile("CN","Inner Mongolia Autonomous Region","CMCC").province.equals("neimenggu"),"Inner Mongolia alias");
        check(profile("CN","Tibet","中国联通").province.equals("xizang"),"Tibet alias");
        check(profile("SG","Singapore","China Mobile")==null,"foreign / VPN endpoint keeps cache");
        check(profile("CN","Unknown","China Mobile")==null,"unknown province keeps cache");
        check(profile("CN","Fujian","Unknown ISP")==null,"unknown ISP keeps cache");
        boolean rejected=false;try{new NetworkProfile("../bad","mobile");}catch(IllegalArgumentException e){rejected=true;}
        check(rejected,"untrusted API cannot supply arbitrary repository paths");
        for(String[] p:NetworkProfile.PROVINCES)for(String[] c:NetworkProfile.CARRIERS){
            var m=new NetworkProfile(p[0],c[0]);check(m.url().contains("/"+p[0]+"/"+c[0]+".txt"),"manual supported profile");
        }
        System.out.println("PASS: network selection, retention, change, unsupported region, validated paths; 93 profiles");
    }
}
