import jadx.api.*;
import java.io.File;
import java.nio.file.*;
import java.util.*;
public class Decompile {
  public static void main(String[] names) throws Exception {
    JadxArgs args=new JadxArgs();
    args.setInputFile(new File(System.getProperty("apk", "evidence/SystemUI.apk")));
    args.setSkipResources(true); args.setThreadsCount(4); args.setShowInconsistentCode(true);
    try(JadxDecompiler d=new JadxDecompiler(args)) {
      d.load();
      for(String name:names) {
        JavaClass c=d.searchJavaClassByOrigFullName(name);
        if(c==null) { System.out.println("MISSING "+name); continue; }
        Files.writeString(Path.of("evidence/"+name.substring(name.lastIndexOf('.')+1)+".java"),c.getCode());
        System.out.println("SAVED "+name);
      }
    }
  }
}
