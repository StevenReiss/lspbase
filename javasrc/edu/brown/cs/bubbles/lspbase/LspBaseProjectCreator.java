/********************************************************************************/
/*                                                                              */
/*              LspBaseProjectCreator.java                                      */
/*                                                                              */
/*      Create a project                                                        */
/*                                                                              */
/********************************************************************************/
/*      Copyright 2011 Brown University -- Steven P. Reiss                    */
/*********************************************************************************
 *  Copyright 2011, Brown University, Providence, RI.                            *
 *                                                                               *
 *                        All Rights Reserved                                    *
 *                                                                               *
 *  Permission to use, copy, modify, and distribute this software and its        *
 *  documentation for any purpose other than its incorporation into a            *
 *  commercial product is hereby granted without fee, provided that the          *
 *  above copyright notice appear in all copies and that both that               *
 *  copyright notice and this permission notice appear in supporting             *
 *  documentation, and that the name of Brown University not be used in          *
 *  advertising or publicity pertaining to distribution of the software          *
 *  without specific, written prior permission.                                  *
 *                                                                               *
 *  BROWN UNIVERSITY DISCLAIMS ALL WARRANTIES WITH REGARD TO THIS                *
 *  SOFTWARE, INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND            *
 *  FITNESS FOR ANY PARTICULAR PURPOSE.  IN NO EVENT SHALL BROWN UNIVERSITY      *
 *  BE LIABLE FOR ANY SPECIAL, INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY          *
 *  DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS,              *
 *  WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS               *
 *  ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE          *
 *  OF THIS SOFTWARE.                                                            *
 *                                                                               *
 ********************************************************************************/



package edu.brown.cs.bubbles.lspbase;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StreamTokenizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Element;

import edu.brown.cs.ivy.exec.IvyExec;
import edu.brown.cs.ivy.file.IvyFile;
import edu.brown.cs.ivy.xml.IvyXml;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

class LspBaseProjectCreator implements LspBaseConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private String  project_name;
private File    project_dir;
private Map<String,Object> prop_map;
private LspBaseLanguageData project_lang;
private String cap_prefix;

private static final String PROJ_PROP_SOURCE = "ProjectSource";
private static final String PROJ_PROP_LIBS = "ProjectLibraries";
private static final String PROJ_PROP_LINKS = "ProjectLinks";
private static final String PROJ_PROP_IGNORES = "ProjectIgnores";



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

LspBaseProjectCreator(String name,File dir,String type,Element props)
{
   project_name = name;
   project_dir = dir;
   project_lang = null;
   LspBaseMain lspbase = LspBaseMain.getLspMain();
   for (LspBaseLanguageData ld : lspbase.getAllLanguageData()) {
      Set<String> ptyps = ld.getCapabilityStrings("lsp.projects.projectTypes");
      if (ptyps.contains(type)) {
         project_lang = ld;
         cap_prefix = "lsp.projects." + type.toLowerCase() + ".";
         break;
       }
    }
   
   setupPropMap(props);
}


/********************************************************************************/
/*                                                                              */
/*      Creation processing                                                     */
/*                                                                              */
/********************************************************************************/

boolean setupProject()
{
   if (project_lang == null) return false;
   
   boolean fg = false;
   String kind = project_lang.getCapabilityString(cap_prefix + "kind");
   switch (kind) {
      case "NEW" :
         fg = setupNewProject();
         break;
      case "SOURCE" :
         fg = setupSourceProject();
         break;
      case "TEMPLATE" :
         fg = setupTemplateProject();
         break;
      case "GIT" :
         fg = setupGitProject();
         break;
    }
   
   if (!fg) return false;
   
   return outputProject();
}



/********************************************************************************/
/*                                                                              */
/*      Setup a completely new project                                          */
/*                                                                              */
/********************************************************************************/

private boolean setupNewProject()
{
   File sdir = new File(project_dir,"src");
   List<File> srcs = new ArrayList<>();
   srcs.add(sdir);
   prop_map.put(PROJ_PROP_SOURCE,srcs);
   String cmd = project_lang.getCapabilityString(cap_prefix + "command");
   if (cmd == null) return false;
   
   if (!sdir.exists() && !sdir.mkdir()) return false;
   
   Map<String,String> bp = new HashMap<>();
   bp.put("PROJECT",project_name);
   bp.put("AUTHOR",System.getProperty("user.name"));
   cmd = IvyFile.expandText(cmd,bp);
   try {
      IvyExec ex = new IvyExec(cmd,null,sdir,IvyExec.ERROR_OUTPUT);
      if (ex.waitFor() != 0) return false;
    }
   catch (IOException ex) {
      return false;
    }
   
   return defineProject(sdir);
}



/********************************************************************************/
/*                                                                              */
/*      Setup Project using existing source                                     */
/*                                                                              */
/********************************************************************************/

private boolean setupSourceProject()
{
   File dir = propFile("SOURCE_DIR");
   if (dir == null) return false;
   
   return defineProject(dir);
}


private boolean defineProject(File dir)
{
   Set<File> libs = new HashSet<>();
   Set<File> rsrcs = new HashSet<>();
   Set<File> srclst = new HashSet<>();
   Set<String> ignores = new HashSet<>();
   
   findFiles(dir,libs,rsrcs,srclst,ignores,0);

   prop_map.put(PROJ_PROP_SOURCE,srclst);
   List<File> liblst = new ArrayList<>();
   for (File f : libs) {
      liblst.add(f);
    }
   for (File f : rsrcs) {
      liblst.add(f);
    }
   prop_map.put(PROJ_PROP_LIBS,liblst);
   prop_map.put(PROJ_PROP_IGNORES,ignores);
   
   return true;
   
}



/********************************************************************************/
/*                                                                              */
/*      Setup template project                                                  */
/*                                                                              */
/********************************************************************************/

private boolean setupTemplateProject()
{
   File dir = propFile("TEMPLATE_DIR");
   File sdir = propFile("SOURCE_DIR");
   try {
      IvyFile.copyHierarchy(dir,sdir);
    }
   catch (IOException e) { 
      return false;
    }
   
   return defineProject(sdir);
}



/********************************************************************************/
/*                                                                              */
/*      Setup a GIT project                                                     */
/*                                                                              */
/********************************************************************************/

private boolean setupGitProject()
{
   File dir = propFile("GIT_DIR");
   prop_map.put("SOURCE_DIR",dir);
   return defineProject(dir);
}



/********************************************************************************/
/*                                                                              */
/*      Find files methods                                                      */
/*                                                                              */
/********************************************************************************/

private void findFiles(File dir,Set<File> libs,Set<File> resources,Set<File> roots,
      Set<String> ignores,int lvl)
{
   if (dir.isDirectory()) {
      Set<File> use = null;
      if (lvl == 0 && !project_lang.getCapabilityBool("lsp.projects.packaged")) {
         JSONArray srcarr = project_lang.getCapabilityArray("lsp.projects.userDirectories");
         if (srcarr != null) {
            for (int i = 0; i < srcarr.length(); ++i) {
               String srcnm = srcarr.getString(i);
               File d1 = new File(dir,srcnm);
               if (d1.exists()) {
                  if (use == null) use = new HashSet<>();
                  use.add(d1);
                } 
             }
          }
         roots.add(dir);
       }
      if (dir.getName().equals("bBACKUP")) {
         ignores.add("**/bBACKUP");
         return;
       }
      else if (dir.getName().startsWith(".")) return;
      else if (dir.getName().equals("resources")) {
         boolean havesrc = false;
         for (File fnm : dir.listFiles()) {
            if (project_lang.isSourceFile(fnm)) havesrc = true;
          }
         if (!havesrc) resources.add(dir);
       }
      Set<String> skip = project_lang.getCapabilitySet("lsp.projects.skip");
      if (skip != null && skip.contains(dir.getName())) return;
      for (File sf : dir.listFiles()) {
         if (use != null && !use.contains(sf)) continue;
         findFiles(sf,libs,resources,roots,ignores,lvl+1);
       }
    }
   else {
      String pnm = dir.getPath();
      dir = IvyFile.getCanonical(dir);
      if (dir.length() < 10) return;
      if (!dir.isFile()) return;
      if (project_lang.isSourceFile(dir)) {
         if (project_lang.getCapabilityBool("lsp.projects.packaged")) {
            String pkg = getPackageName(dir);
            File par = dir.getParentFile();
            if (pkg != null) {
               String [] ps = pkg.split("\\.");
               for (int i = ps.length-1; par != null && i >= 0; --i) {
                  if (!par.getName().equals(ps[i])) par = null;
                  else par = par.getParentFile();
                }
             }
            if (par != null) {
               roots.add(par);
             }
          }
       }
      else {
         JSONArray libtyps = project_lang.getCapabilityArray("lsp.projects.libraryExtensions");
         if (libtyps != null) {
            for (int i = 0; i < libtyps.length(); ++i) {
               String libext = libtyps.getString(i);
               if (pnm.endsWith(libext)) {
                  libs.add(dir);
                }
             }
          }
       }
    }
}


private static String getPackageName(File src)
{
   try {
      FileReader fis = new FileReader(src);
      StreamTokenizer str = new StreamTokenizer(fis);
      str.slashSlashComments(true);
      str.slashStarComments(true);
      str.eolIsSignificant(false);
      str.lowerCaseMode(false);
      str.wordChars('_','_');
      str.wordChars('$','$');
      
      StringBuilder pkg = new StringBuilder();
      
      for ( ; ; ) {
         int tid = str.nextToken();
         if (tid == StreamTokenizer.TT_WORD) {
            if (str.sval.equals("package")) {
               for ( ; ; ) {
                  int nid = str.nextToken();
                  if (nid != StreamTokenizer.TT_WORD) break;
                  pkg.append(str.sval);
                  nid = str.nextToken();
                  if (nid != '.') break;
                  pkg.append(".");
                }
               break;
             }
            else break;
          }
         else if (tid == StreamTokenizer.TT_EOF) {
            return null;
          }
       }
      
      fis.close();
      return pkg.toString();
    }
   catch (IOException e) {
    }
   
   return null;
}



/********************************************************************************/
/*                                                                              */
/*      Ouptut methods                                                          */
/*                                                                              */
/********************************************************************************/


private boolean outputProject()
{
   JSONArray props = project_lang.getCapabilityArray("lsp.projects.propertySets");
   if (props != null) {
      for (File f : propSources()) {
         checkFileProperties(f,props);
       }
    }
   
   if (!generateProjectFile()) return false;
   if (!generateSettingsFile()) return false;
   if (!generateOtherFiles()) return false;
   
   return true;
}



private void checkFileProperties(File f,JSONArray checks)
{
   if (f.isDirectory()) {
      for (File sub : f.listFiles()) {
         checkFileProperties(sub,checks);
       }
    }
   else if (project_lang.isSourceFile(f)) {
      try {
         String cnts = IvyFile.loadFile(f);
         for (int i = 0; i < checks.length(); ++i) {
            JSONObject check = checks.getJSONObject(i);
            String find = check.optString("lookFor");
            if (find == null || cnts.contains(find)) {
              String fnm = check.optString("file");
              if (fnm == null || f.getName().equals(fnm)) {
                 String prop = check.optString("property");
                 if (prop != null) prop_map.put(prop,true);
                 String pkgprop = check.optString("packageProperty");
                 if (pkgprop != null) {
                    String pkg = getPackageName(f);
                    if (pkg != null) prop_map.put(pkgprop,pkg);
                  }
               }
             }
          }
       }
      catch (IOException e) { }
    }
}


private boolean generateProjectFile()
{
   // might need eclipse-specific here
   
   try (IvyXmlWriter xw = new IvyXmlWriter(new File(project_dir,".lspproject"))) {
      xw.outputHeader();
      xw.begin("PROJECT");
      xw.field("NAME",project_name);
      xw.field("LANGUAGE",project_lang.getName().toLowerCase());
      xw.field("BASE",project_dir);
      for (File f : propSources()) {
         xw.begin("PATH");
         xw.field("ID",f.hashCode());
         xw.field("SOURCE",f);
         xw.field("TYPE","INCLUDE");
         xw.field("NEST",true);
         xw.end("PATH");
       }
      for (File f : propLibraries()) {
         xw.begin("PATH");
         xw.field("ID",f.hashCode());
         xw.field("BINARY",f);
         xw.field("TYPE","LIBRARY");
         // handle nested
         xw.end("PATH");
       }
      for (String pat : propStrings(PROJ_PROP_IGNORES)) {
         xw.begin("PATH");
         xw.field("ID",pat.hashCode());
         xw.field("SOURCE",pat);
         xw.field("TYPE","EXCLUDE");
         // handle nested
         xw.end("PATH");
       }
      
    }
   catch (Throwable t) {
      return false;
    }
   
   return true;
}


private boolean generateSettingsFile()
{
   // only used by Eclipse   
   return true;
}


private boolean generateOtherFiles()
{
   // only used by Eclipse
   return true;
}






/********************************************************************************/
/*                                                                              */
/*      Property methods                                                        */
/*                                                                              */
/********************************************************************************/

private void setupPropMap(Element props)
{
   prop_map = new HashMap<>();
   for (Element pelt : IvyXml.children(props,"PROP")) {
      String pnm = IvyXml.getAttrString(pelt,"NAME");
      Object fval = getPropertyValue(pelt);
      if (fval != null) prop_map.put(pnm,fval);
    }
}


private Object getPropertyValue(Element pelt)
{
   String typ = IvyXml.getAttrString(pelt,"TYPE");
   String val = IvyXml.getTextElement(pelt,"VALUE");
   Object fval = null;
   switch (typ) {
      case "int" :
         fval = Integer.valueOf(val);
         break;
      case "String" :
         fval = val;
         break;
      case "boolean" :
         fval = Boolean.valueOf(val);
         break;
      case "File" :
         fval = new File(val);
         break;
      case "List" :
         List<Object> l = new ArrayList<>();
         for (Element lelt : IvyXml.children(pelt,"PROP")) {
            Object v = getPropertyValue(lelt);
            if (v != null) l.add(v);
          }
         fval = l;
         break;
      case "Map" :
         Map<String,Object> m = new HashMap<>();
         for (Element melt : IvyXml.children(pelt,"PROP")) {
            String k = IvyXml.getAttrString(melt,"NAME");
            Object v = getPropertyValue(melt);
            if (k != null && v != null) m.put(k,v);
          }
         fval = m;
         break;
    }
   
   return fval;
}



@SuppressWarnings("unused")
private String propString(String k)
{
   Object v = prop_map.get(k);
   if (v == null) return null;
   return v.toString();
}


@SuppressWarnings("unused")
private boolean propBool(String k)
{
   Object v = prop_map.get(k);
   if (v == null) return false;
   if (v instanceof Boolean) return ((Boolean) v);
   return false;
}


private List<File> propSources()
{
   return propFiles(PROJ_PROP_SOURCE);
}

private List<File> propLibraries()
{
   return propFiles(PROJ_PROP_LIBS);
}

@SuppressWarnings("unused")
private Map<String,File> propLinks()
{
   Map<String,File> rslt = new HashMap<>();
   Map<?,?> lnks = (Map<?,?>) prop_map.get(PROJ_PROP_LINKS);
   if (lnks != null) {
      for (Map.Entry<?,?> ent : lnks.entrySet()) {
         String k = ent.getKey().toString();
         Object v1 = ent.getValue();
         File v = null;
         if (v1 instanceof File) v = (File) v1;
         else if (v1 instanceof String) v = new File((String) v1);
         if (v != null) rslt.put(k,v);
       }
    }
   return rslt;
}



private List<File> propFiles(String k)
{
   Collection<?> ps = (Collection<?>) prop_map.get(k);
   List<File> rslt = new ArrayList<>();
   if (ps != null) {
      for (Object o : ps) {
         if (o instanceof File) rslt.add((File) o);
         else if (o instanceof String) rslt.add(new File((String) o));
       }
    }
   return rslt;
}



private File propFile(String k)
{
   Object o = prop_map.get(k);
   if (o == null) return null;
   if (o instanceof File) return (File) o;
   return new File(o.toString());
}


private List<String> propStrings(String k) 
{
   Collection<?> ps = (Collection<?>) prop_map.get(k);
   List<String> rslt = new ArrayList<>();
   if (ps != null) {
      for (Object o : ps) {
         rslt.add(o.toString());
       }
    }
   return rslt;
}



}       // end of class LspBaseProjectCreator




/* end of LspBaseProjectCreator.java */

