import jadx.api.*;
import java.io.File;
public class FindClasses {
 public static void main(String[] args) throws Exception {
  JadxArgs a = new JadxArgs(); a.setInputFile(new File(System.getProperty("apk", "evidence/SystemUI.apk"))); a.setSkipResources(true);
  try(JadxDecompiler d = new JadxDecompiler(a)) { d.load(); for(JavaClass c:d.getClasses()) {
   String n=c.getFullName(); for(String p:args) if(n.toLowerCase().contains(p.toLowerCase())) {System.out.println(n);break;}
  }}
 }
}
