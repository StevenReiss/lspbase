/********************************************************************************/
/*										*/
/*		LspBaseProject.java						*/
/*										*/
/*	description of class							*/
/*										*/
/********************************************************************************/
/*	Copyright 2011 Brown University -- Steven P. Reiss		      */
/*********************************************************************************
 *  Copyright 2011, Brown University, Providence, RI.				 *
 *										 *
 *			  All Rights Reserved					 *
 *										 *
 * This program and the accompanying materials are made available under the	 *
 * terms of the Eclipse Public License v1.0 which accompanies this distribution, *
 * and is available at								 *
 *	http://www.eclipse.org/legal/epl-v10.html				 *
 *										 *
 ********************************************************************************/



package edu.brown.cs.bubbles.lspbase;

import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Element;
import org.yaml.snakeyaml.Yaml;

import edu.brown.cs.ivy.exec.IvyExec;
import edu.brown.cs.ivy.file.IvyFile;
import edu.brown.cs.ivy.xml.IvyXml;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

class LspBaseProject implements LspBaseConstants
{


/********************************************************************************/
/*										*/
/*	Private Storage 							*/
/*										*/
/********************************************************************************/

private LspBaseMain	lsp_base;
private LspBaseProjectManager project_manager;
private String		project_name;
private String		project_language;
private File		base_directory;
private List<LspBasePathSpec> project_paths;
private Map<String,LspBaseFile> file_map;
private List<LspBaseFile> project_files;
private boolean 	is_open;
private LspBaseProtocol use_protocol;
private LspBasePreferences project_preferences;
private Map<String,EditParameters> edit_parameters;
private Set<String> project_references;




/********************************************************************************/
/*										*/
/*	Constructors								*/
/*										*/
/********************************************************************************/

LspBaseProject(LspBaseMain lm,LspBaseProjectManager pm,String name,File base)
{
   lsp_base = lm;
   project_manager = pm;
   base_directory = IvyFile.getCanonical(base);
   if (name == null) name = base.getName();
   project_name = name;
   project_paths = new ArrayList<>();
   project_files = new ArrayList<>();
   file_map = new HashMap<>();
   project_preferences = new LspBasePreferences(pm.getSystemPreferences());
   edit_parameters = new HashMap<>();
   project_references = new HashSet<>();

   File f = new File(base_directory,".bubbles");
   if (!f.exists()) f.mkdir();

   File f1 = new File(base_directory,PROJECT_DATA_FILE);
   Element xml = IvyXml.loadXmlFromFile(f1);
   if (xml != null) {
      project_language = IvyXml.getAttrString(xml,"LANGUAGE","dart");
      for (Element pe : IvyXml.children(xml,"PATH")) {
	 LspBasePathSpec ps = project_manager.createPathSpec(pe);
	 project_paths.add(ps);
       }
      for (Element fe : IvyXml.children(xml,"FILE")) {
	 String nm = IvyXml.getTextElement(fe,"NAME");
	 File fs = new File(nm);
	 addLspFile(fs,false);
       }
      for (Element re : IvyXml.children(xml,"REFERENCES")) {
         project_references.add(IvyXml.getAttrString(re,"NAME"));
       }
      project_preferences.loadXml(xml);
    }
   is_open = false;
   use_protocol = null;
// use_protocol = lsp_base.findProtocol(base_directory,project_language);
   saveProject();
}



/********************************************************************************/
/*										*/
/*	Access methods								*/
/*										*/
/********************************************************************************/

String getName()				{ return project_name; }
String getLanguage()				{ return project_language; }
LspBaseProtocol getProtocol()			{ return use_protocol; }
LspBaseLanguageData getLanguageData() 
{
   return lsp_base.getLanguageData(getLanguage());
}
File getBasePath()				{ return base_directory; }
// NobasePreferences getPreferences()		{ return nobase_prefs; }
boolean isOpen()				{ return is_open; }
boolean exists()				{ return base_directory.exists(); }

List<LspBaseProject> getReferencedProjects()	
{ 
   List<LspBaseProject> rslt = new ArrayList<>();
   for (String s : project_references) {
      try {
         LspBaseProject bp = project_manager.findProject(s);
         if (bp != null) rslt.add(bp);
       }
      catch (LspBaseException e) { }
    }
   return rslt;
}

List<LspBaseProject> getReferencingProjects()
{ 
   List<LspBaseProject> rslt = new ArrayList<>();
   for (LspBaseProject bp : project_manager.getAllProjects()) {
      if (bp == this) continue;
      if (bp.project_references.contains(project_name)) {
         rslt.add(bp);
       }
    }
   return rslt;
}

LspBasePreferences getPreferences()		{ return project_preferences; }

int getDelayTime(String bid)
{
   EditParameters ep = getParameters(bid);
   return ep.getDelayTime();
}

boolean getAutoElide(String bid)
{
   EditParameters ep = getParameters(bid);
   return ep.getAutoElide();
}


LspBaseFile findFile(String path)
{
   if (path == null) return null;
   if (path.startsWith("file:/")) {
      int j = 0;
      for (int i = 5; i < path.length(); ++i) {
	 if (path.charAt(i) == '/') j = i;
	 else break;
       }
      path = path.substring(j);
    }
   
   return file_map.get(path);
}

LspBaseFile findFile(File f)
{
   return findFile(f.getAbsolutePath());
}

String getRelativeFile(LspBaseFile f)
{
   String p0 = IvyFile.getCanonicalPath(f.getFile());

   for (LspBasePathSpec ps : project_paths) {
      if (ps.isUser()) {
	 File dir = ps.getFile();
	 File dir1 = getUserSourceDirectory(dir);
	 if (dir1 == null) continue;
	 String p1 = IvyFile.getCanonicalPath(dir1);
	 if (p0.startsWith(p1)) {
	    String p2 = p0.substring(p1.length()+1);
	    return p2;
	  }
       }
    }

   return null;
}



/********************************************************************************/
/*										*/
/*	Open methods								*/
/*										*/
/********************************************************************************/

void open() throws LspBaseException
{
   if (is_open) return;

   if (use_protocol == null) {
      use_protocol = lsp_base.findProtocol(base_directory,project_language,project_paths);
    }
   
   addLibraries();
   
   for (LspBasePathSpec ps : project_paths) {
      if (ps.isUser()) {
	 File dir = ps.getFile();
	 File dir1 = getUserSourceDirectory(dir);
	 if (dir1 != null) {
	    findFiles(ps,null,dir1,false);
	  }
       }
    }

   use_protocol.initialize();

   is_open = true;
}




private File getUserSourceDirectory(File dir)
{
   for (File f1 = dir; f1 != null; f1 = f1.getParentFile()) {
      File fyaml = new File(f1,"pubspec.yaml");
      File flib = new File(f1,"lib");
      if (fyaml.exists() && flib.exists() && flib.isDirectory()) {
	 return flib;
       }
    }

   return null;
}



protected void findFiles(LspBasePathSpec spec,String pfx,File f,boolean reload)
{
   boolean nest = spec.isNested() || f.equals(spec.getFile());
   if (!spec.useFile(f)) return;

   FileFilter filter = lsp_base.getLanguageData(project_language).getSourceFilter();

   if (f.isDirectory()) {
      File [] fls = f.listFiles(filter);
      String npfx = null;
      if (pfx != null) npfx = pfx + "." + f.getName();
      for (File f1 : fls) {
	 if (!nest && f1.isDirectory()) continue;
	 findFiles(spec,npfx,f1,reload);
       }
      return;
    }

   if (!filter.accept(f)) return;
   addLspFile(f,reload);
}


private LspBaseFile addLspFile(File file,boolean reload)
{
   LspBaseFile lbf0 = findFile(file);
   if (lbf0 != null) {
      if (reload) lbf0.reload();
      return lbf0;
    }

   LspLog.logD("Add File " + file.getAbsolutePath());

   LspBaseFile lbf = new LspBaseFile(this,file,project_language);
   project_files.add(lbf);
   file_map.put(file.getAbsolutePath(),lbf);
   try {
      file_map.put(file.getCanonicalPath(),lbf);
    }
   catch (IOException e) { }

   return lbf;
}


private void removeLspFile(LspBaseFile oldf)
{
   project_files.remove(oldf);
   for (Iterator<Map.Entry<String,LspBaseFile>> it = file_map.entrySet().iterator(); it.hasNext(); ) {
      Map.Entry<String,LspBaseFile> ent = it.next();
      if (ent.getValue() == oldf) it.remove();
    }
}


private LspBaseFile renameLspFile(LspBaseFile oldf,File file)
{
   removeLspFile(oldf);
   
   return addLspFile(file,true);
}



/********************************************************************************/
/*                                                                              */
/*      Library methods                                                         */
/*                                                                              */
/********************************************************************************/

@SuppressWarnings("unchecked")
private void addLibraries()
{
   String typ = getLanguageData().getCapabilityString("lsp.projects.library");
   if (typ == null) return;
   List<LspBasePathSpec> toadd = new ArrayList<>();
   for (LspBasePathSpec path : project_paths) {
      if (!path.isUser()) continue;
      File f1 = path.getFile();
      File f2 = null;
      switch (typ) {
         case "pubspec.yaml" :
            f2 = new File(f1,"pubspec.yaml");
            if (!f2.exists()) f2 = new File(f1,"pubspec.yml");
            if (!f2.exists()) continue;
            try (FileReader fr = new FileReader(f2)) {
               Yaml yaml = new Yaml();
               Map<String,Object> r = new HashMap<>();
               r = yaml.loadAs(fr,Map.class);
               Map<String,Object> o = (Map<String,Object>) r.get("dependencies");
               if (o == null) continue;
               for (String k : o.keySet()) {
                  Object v = o.get(k);
                  if (v instanceof String) {
                     toadd.add(new LspBasePathSpec(k,o.toString()));
                   }
                  else if (v instanceof Map<?,?>) {
                     Map<?,?> m = (Map<?,?>) v;
                     toadd.add(new LspBasePathSpec(k,m.toString()));
                   }
                  else {
                     System.err.println("DEP " + k + " = " + o.get(k));
                   }
                }
             }
            catch (IOException e) { }
            
            break;
         case "package.json" :
            f2 = new File(f1,"package.json");
            if (!f2.exists()) continue;
            try {
               String cnts = IvyFile.loadFile(f2);
               JSONObject jobj = new JSONObject(cnts);
               JSONObject dep = jobj.optJSONObject("dependencies");
               if (dep == null) continue;
               for (String k : dep.keySet()) {
                  toadd.add(new LspBasePathSpec(k,dep.get(k).toString()));
                }
             }
            catch (IOException e) { }
            break;
         default :
            continue;
       }
    }
   for (Iterator<LspBasePathSpec> it = toadd.iterator(); it.hasNext(); ) {
      LspBasePathSpec add = it.next();
      for (LspBasePathSpec path : project_paths) {
         if (add.getFile().equals(path.getFile())) {
            it.remove();
            break;
          }
       }
    }
   project_paths.addAll(toadd);
}


/********************************************************************************/
/*                                                                              */
/*      Editing methods                                                         */
/*                                                                              */
/********************************************************************************/

void editProject(Element xml)
{
   for (Element pxml : IvyXml.children(xml,"PATH")) {
      updatePathEntry(pxml);
    }
   
   Set<String> refs = new HashSet<>();
   Element rxml = IvyXml.getChild(xml,"REFERENCES");
   for (Element ref : IvyXml.children(rxml,"PROJECT")) {
      String txt = IvyXml.getText(ref);
      refs.add(txt);
    }
   project_references = refs;
   
   project_preferences.setPreferences(xml);
}


private void updatePathEntry(Element pxml)
{
   LspBasePathSpec nspec = new LspBasePathSpec(pxml);
   
   int id = IvyXml.getAttrInt(pxml,"ID",0);
   LspBasePathSpec edit = null;
   if (id != 0) {
      for (LspBasePathSpec bps : project_paths) {
         if (bps.getId() == id) {
            edit = bps;
            break;
          }
       }
    }
   else {
      for (LspBasePathSpec bps : project_paths) {
         if (bps.getFile().equals(nspec.getFile())) {
            edit = bps;
            break;
          }
       }
    }
   
   if (IvyXml.getAttrBool(pxml,"DELETE")) {
      if (edit != null) removePathEntry(edit);
    }
   else if (edit != null) {
      edit.updateFrom(nspec);
    }
   else {
      addPathEntry(nspec);
    }
}


private void addPathEntry(LspBasePathSpec ps)
{
   String typ = getLanguageData().getCapabilityString("lsp.projects.library");
   String cmd = getLanguageData().getCapabilityString("lsp.projects.libraryAdd");
   executeLibraryCommand(typ,cmd,ps);
   
   project_paths.add(ps);
}


private void removePathEntry(LspBasePathSpec ps)
{
   String typ = getLanguageData().getCapabilityString("lsp.projects.library");
   String cmd = getLanguageData().getCapabilityString("lsp.projects.libraryRemove");
   executeLibraryCommand(typ,cmd,ps);
   
   project_paths.remove(ps);
}

      
private void executeLibraryCommand(String typ,String cmd,LspBasePathSpec ps)
{
   if (typ == null || cmd == null) return;
   
   for (LspBasePathSpec path : project_paths) {
      if (!path.isUser()) continue;
      File f1 = path.getFile();
      File f2 = new File(f1,typ);
      if (!f2.exists()) continue;
      Map<String,String> m = new HashMap<>();
      m.put("LIB",ps.getFile().getPath());
      String ver = ps.getInfo();
      if (ver != null) m.put("INFO",ver);
      String ecmd = IvyFile.expandText(cmd,m);
      try {
         IvyExec ex = new IvyExec(ecmd,f1);
         ex.waitFor();
       }
      catch (IOException e) { }
    }
}



/********************************************************************************/
/*										*/
/*	Names methods								*/
/*										*/
/********************************************************************************/

void getAllNames(LspNamer namer)
{
   for (LspBaseFile lbf : project_files) {
      NameHandler nh = new NameHandler(namer,this,lbf);
      nh.handleResponse(lbf.getSymbols());
//    JSONObject tdi = createJson("uri",lbf.getUri());
//    use_protocol.sendMessage("textDocument/documentSymbol",
// 	    new NameHandler(namer,this,lbf),
// 	    "textDocument",tdi);
    }
}


private class NameHandler implements LspArrayResponder {

   private LspBaseProject for_project;
   private LspBaseFile for_file;
   private LspNamer use_namer;

   NameHandler(LspNamer namer,LspBaseProject project,LspBaseFile file) {
      for_project = project;
      for_file = file;
      use_namer = namer;
    }


   @Override public void handleResponse(JSONArray jarr) {
      use_namer.handleNames(for_project,for_file,jarr);
    }

}	// end of inner class NameHandler




/********************************************************************************/
/*										*/
/*	Build methods								*/
/*										*/
/********************************************************************************/

void commit(String bid,boolean refresh,
      boolean save,boolean compile,
      List<Element> files,IvyXmlWriter xw)
{
   if (files == null || files.size() == 0) {
      for (LspBaseFile lbf : project_files) {
	 if (refresh || !save || lbf.hasChanged()) {
	    commitFile(lbf,bid,refresh,save,compile,xw);
	  }
       }
    }
   else {
      for (Element e : files) {
	 String fnm = IvyXml.getAttrString(e,"NAME");
	 LspBaseFile lbf = findFile(fnm);
	 if (lbf != null) {
	    boolean r = IvyXml.getAttrBool(e,"REFRESH",refresh);
	    boolean s = IvyXml.getAttrBool(e,"SAVE",save);
            boolean c = IvyXml.getAttrBool(e,"COMPILE",compile);
	    commitFile(lbf,bid,r,s,c,xw);
	  }
       }
    }
}


private void commitFile(LspBaseFile lbf,String bid,
      boolean refresh,boolean save,boolean compile,IvyXmlWriter xw)
{
   boolean upd = false;
   
   if (xw != null) {
      xw.begin("FILE");
      xw.field("NAME",lbf.getPath());
    }
   
   lbf.lockFile(bid);
   try {
      try {
	 upd = lbf.commit(refresh,save,compile);
       }
      catch (Throwable t) {
	 xw.field("ERROR",t.toString());
       }
    }
   finally {
      lbf.unlockFile();
    }
   
   if (xw != null) {
      xw.end("FILE");
    }
   
   if (upd) {
      // start autocompile
    }

}


void build(boolean refresh,boolean reload)
{
   if (!is_open) {
      try { 
         open();
       }
      catch (LspBaseException e) { }
      return;
    }

   Set<LspBaseFile> oldfiles = null;
   if (refresh) {
      oldfiles = new HashSet<>(project_files);
      if (reload) {
	 project_files.clear();
       }
    }

   for (LspBasePathSpec ps : project_paths) {
      if (ps.isUser()) {
	 File dir = ps.getFile();
	 findFiles(ps,null,dir,reload);
       }
    }
   
   if (oldfiles != null) {
      handleRefresh(oldfiles);
    }
}


private void handleRefresh(Set<LspBaseFile> oldfiles)
{
   IvyXmlWriter xw = lsp_base.beginMessage("RESOURCE");
   int ctr = 0;
   for (LspBaseFile fd : project_files) {
      LspBaseFile old = null;
      for (LspBaseFile ofd : oldfiles) {
         if (ofd.getFile().equals(fd.getFile())) {
            old = ofd;
            break;
          }
       }
      if (old == null) {
         outputDelta(xw,"ADDED",fd);
         ++ctr;
       }
      else if (old.getFile().lastModified() != fd.getFile().lastModified()) {
         oldfiles.remove(old);
         outputDelta(xw,"CHANGED",fd);
         ++ctr;
       }
      else {
         oldfiles.remove(old);
       }
    }
   for (LspBaseFile fd : oldfiles) {
      outputDelta(xw,"REMOVED",fd);
      ++ctr;
    }
   if (ctr > 0) {
      lsp_base.finishMessage(xw);
    }
}



private void outputDelta(IvyXmlWriter xw,String act,LspBaseFile ifd)
{
   xw.begin("DELTA");
   xw.field("KIND",act);
   xw.begin("RESOURCE");
   xw.field("TYPE","FILE");
   xw.field("PROJECT",project_name);
   xw.field("LOCATION",ifd.getFile().getAbsolutePath());
   xw.end("RESOURCE");
   xw.end("DELTA");
}




/********************************************************************************/
/*										*/
/*	Search methods								*/
/*										*/
/********************************************************************************/

void safePatternSearch(String pat,String typ,boolean defs,boolean refs,boolean system,IvyXmlWriter xw)
{
   try {
      patternSearch(pat,typ,defs,refs,system,xw);
    }
   catch (LspBaseException e) {
      LspLog.logE("Problem in pattern search",e);
    }
}
   
   
   
void patternSearch(String pat,String typ,boolean defs,boolean refs,boolean system,IvyXmlWriter xw)
   throws LspBaseException
{
   LspBasePatternResult sr = new LspBasePatternResult(this,pat,typ,system);
   String fpat = sr.getMatchPattern();
   use_protocol.sendWorkMessage("workspace/symbol",sr,"query",fpat);

   List<JSONObject> rslts = sr.getResults();
   if (rslts.isEmpty()) return;

   if (defs && !refs) {
      for (JSONObject symobj : rslts) {
         LspBaseUtil.outputLspSymbol(this,null,symobj,xw);
       }
    }
   else {
      LspLog.logE("Attempt to get references from pattern search");
    }
}



void findAll(LspBaseFile file,int start,int end,boolean defs,boolean refs,boolean impls,
      boolean type,boolean ronly,boolean wonly,IvyXmlWriter xw)
   throws LspBaseException
{
   LineCol lc = file.mapOffsetToLineColumn(start);
   LspBaseFindResult rslt = new LspBaseFindResult(this,file,defs,refs,
         impls,type,ronly,wonly);
   
   if (refs) {
      use_protocol.sendWorkMessage("textDocument/references",
            (JSONArray data) -> rslt.addResults(data,"REFS"),
            "textDocument",file.getTextDocumentId(),
            "position",createJson("line",lc.getLspLine(),"character",lc.getLspColumn()),
            "context",createJson("includeDeclaration",defs));
    }
   
   if (ronly || wonly) {
      use_protocol.sendWorkMessage("textDocument/documentHighlight",
            (Object data) -> rslt.addResults(data,"HIGH"),
            "textDocument",file.getTextDocumentId(),
            "position",createJson("line",lc.getLspLine(),"character",lc.getLspColumn()));
    }
   
   if (!type) {
      if (getLanguageData().getCapability("declarationProvider") != null) {
         use_protocol.sendWorkMessage("textDocument/declaration",
               (Object data) -> rslt.addResults(data,"DECL"),
               "textDocument",file.getTextDocumentId(),
               "position",createJson("line",lc.getLspLine(),"character",lc.getLspColumn()));
       }
      
      use_protocol.sendWorkMessage("textDocument/definition",
            (Object data) -> rslt.addResults(data,"DEFS"),
            "textDocument",file.getTextDocumentId(),
            "position",createJson("line",lc.getLspLine(),"character",lc.getLspColumn()));
    }
   else {
      use_protocol.sendWorkMessage("textDocument/typeDefinition",
            (Object data) -> rslt.addResults(data,"TYPE"),
            "textDocument",file.getTextDocumentId(),
            "position",createJson("line",lc.getLspLine(),"character",lc.getLspColumn())); 
    }
   
   if (impls) {
      // might need to be done before definitions?
      use_protocol.sendWorkMessage("textDocument/implementation",
            (Object data) -> rslt.addResults(data,"IMPL"),
            "textDocument",file.getTextDocumentId(),
            "position",createJson("line",lc.getLspLine(),"character",lc.getLspColumn())); 
    }
   
   List<FindResult> rslts = rslt.getResults();
   if (rslts.isEmpty()) return;
   JSONObject def = null;
   for (FindResult symloc : rslts) {
      def = symloc.getDefinition();
      if (def != null) break;
    }
   if (def != null) {
      xw.begin("SEARCHFOR");
      int kind = def.getInt("kind");
      switch (SymbolKinds[kind]) {
         case "Field" :
         case "Variable" :
         case "Property" :
         case "EnumMember" :
            xw.field("TYPE","Field");
            break;
         case "Local" :
            xw.field("TYPE","Local");
            break;
         case "Function" :
         case "Constructor" :
         case "Method" :
            xw.field("TYPE","Function");
            break;
         case "Class" :
         case "Enum" :
         case "Interface" :
         case "Struct" :
            xw.field("TYPE","Class");
            break;
       }
      xw.text(def.getString("name"));
      xw.end("SEARCHFOR");
    }
   for (FindResult symloc : rslts) {
      LspLog.logD("OUTPUT "  + symloc.getFile().getPath() + " " +
            symloc.getRange() + " " + symloc.getDefinition());
      // output result
      LspBaseUtil.outputFindResult(symloc,xw);
    }
}




/********************************************************************************/
/*                                                                              */
/*      Fully qualified name query                                              */
/*                                                                              */
/********************************************************************************/

void fullyQualifiedName(LspBaseFile file,int start,int end,IvyXmlWriter xw)
   throws LspBaseException
{
   LineCol lc = file.mapOffsetToLineColumn(start);
   LspBaseFindResult rslt = new LspBaseFindResult(this,file,true,false,
         false,false,false,false);
   use_protocol.sendMessage("textDocument/definition",
         (Object data) -> rslt.addResults(data,"DEFS"),
         "textDocument",file.getTextDocumentId(),
         "position",createJson("line",lc.getLspLine(),"character",lc.getLspColumn()));
   for (FindResult fr : rslt.getResults()) {
      JSONObject def = fr.getDefinition();
      if (def != null) {
         xw.begin("FULLYQUALIFIEDNAME");
         String fpfx = getRelativeFile(file);
         String nm = def.getString("name");
         String pfx = def.optString("prefix",null);
         if (pfx != null) nm = fpfx + ";" + pfx + "." + nm;
         xw.field("NAME",nm);
         String det = def.optString("detail",null);
         if (det != null) xw.field("TYPE",det);
         xw.end("FULLYQUALIFIEDNAME");
         break;
       }
    }
}


/********************************************************************************/
/*                                                                              */
/*      Find by key query                                                       */
/*                                                                              */
/********************************************************************************/

void findByKey(LspBaseFile lbf,String key,IvyXmlWriter xw)
{
   String proj = null;
   int idx = key.indexOf(":");
   if (idx > 0) {
      proj = key.substring(0,idx);
      key = key.substring(idx+1);
    }
   String filpfx = null;
   idx = key.indexOf("#");
   if (idx > 0) {
      filpfx = key.substring(0,idx);
      key = key.substring(idx+1);
    }
   LspLog.logD("CHECK KEY " + filpfx + " " + lbf.getPath() + " " + proj + " " + getName());
   
   JSONArray syms = lbf.getSymbols();
   for (int i = 0; i < syms.length(); ++i) {
      JSONObject sym = syms.getJSONObject(i);
      String pfx = sym.optString("prefix",null);
      String nm = sym.getString("name");
      if (pfx != null) nm = pfx + "." + nm;
      if (nm.equals(key)) {
         LspBaseUtil.outputLspSymbol(this,lbf,sym,xw);
         break;
       }
    }
}



/********************************************************************************/
/*                                                                              */
/*      Delete and Rename methods                                               */
/*                                                                              */
/********************************************************************************/

void handleDeleteResource(String what,String path)
   throws LspBaseException
{
   List<LspBaseFile> files = new ArrayList<>();
   File ff = null;
   
   switch (what) {
      case "FILE" :
         ff = new File(what);
         break;
      case "PROJECT" :
         // handled by project manager
         return;
      case "PACKAGE" :
         // TODO: get directory associated with package
      case "CLASS" :
         // TODO: should be handled by finding class and deleting inside a file
         // if the file is all the class, then delete the file
         break;
    }
   
   if (ff != null) {
      addFileToDelete(ff,files);
      if (!files.isEmpty()) {
         willDeleteFiles(files);
         for (LspBaseFile lbf : files) {
            removeLspFile(lbf);
            lbf.getFile().delete();
          }
         didDeleteFiles(files);
       }
    }
}



private void addFileToDelete(File f,List<LspBaseFile> rslt)
{
    if (f.isDirectory()) {
       for (File f1 : f.listFiles()) {
          addFileToDelete(f1,rslt);
        }
     }
    else if (getLanguageData().isSourceFile(f)) {
       LspBaseFile lbf = findFile(f);
       if (lbf != null) rslt.add(lbf);
     }
}


void handleRenameResource(String bid,String file,String newname,IvyXmlWriter xw)
   throws LspBaseException
{
   LspBaseFile lbf = findFile(file);
   if (lbf == null) return;
   File f = new File(newname);
   LspBaseFile newlbf = new LspBaseFile(this,f,project_language);
   Map<LspBaseFile,LspBaseFile> renames = new HashMap<>();
   renames.put(lbf,newlbf);
   willRenameFiles(renames);
   lbf.getFile().renameTo(newlbf.getFile());
   newlbf = renameLspFile(lbf,f);
   renames.put(lbf,newlbf);
   didRenameFiles(renames);
}




/********************************************************************************/
/*										*/
/*	File methods								*/
/*										*/
/********************************************************************************/

void openFile(LspBaseFile lbf) throws LspBaseException
{
   use_protocol.sendMessage("textDocument/didOpen",
	 "textDocument",lbf.getTextDocumentItem());
}




void willSaveFile(LspBaseFile lbf) throws LspBaseException
{
   if (lbf.getLanguageData().getCapabilityBool("textDocumentSync.willSave")) {
      use_protocol.sendMessage("textDocument/willSave",
            "textDocument",lbf.getTextDocumentId(),"reason",1);
    }
}



void didSaveFile(LspBaseFile lbf) throws LspBaseException
{ 
   if (lbf.getLanguageData().getCapabilityBool("textDocumentSync.Save")) {
      use_protocol.sendMessage("textDocument/didSave",
            "textDocument",lbf.getTextDocumentId());
    }
}


void didCloseFile(LspBaseFile lbf) throws LspBaseException
{
   use_protocol.sendMessage("textDocument/didClose",
	 "textDocument",lbf.getTextDocumentId());
}


void willDeleteFiles(List<LspBaseFile> files) throws LspBaseException
{
   if (files.isEmpty()) return;
   LspBaseFile f0 = files.get(0);
   if (f0.getLanguageData().getCapabilityBool("workspace.fileOperations.willDelete")) {
      use_protocol.sendMessage("workspace/willDeleteFiles",
            "files",getFileArray(files));
    }
}



void didDeleteFiles(List<LspBaseFile> files) throws LspBaseException
{ 
   if (files.isEmpty()) return;
   LspBaseFile f0 = files.get(0);
   if (f0.getLanguageData().getCapabilityBool("workspace.fileOperations.didDelete")) {
      use_protocol.sendMessage("workspace/didDeleteFiles",
            "files",getFileArray(files));
    }
}



void willRenameFiles(Map<LspBaseFile,LspBaseFile> files) throws LspBaseException
{
   if (files.isEmpty()) return;
   if (getLanguageData().getCapabilityBool("workspace.fileOperations.willRename")) {
      use_protocol.sendMessage("workspace/willRenameFiles",
            "files",getFileArray(files));
    }
}



void didRenameFiles(Map<LspBaseFile,LspBaseFile> files) throws LspBaseException
{ 
   if (files.isEmpty()) return;
   if (getLanguageData().getCapabilityBool("workspace.fileOperations.didRename")) {
      use_protocol.sendMessage("workspace/didRenameFiles",
            "files",getFileArray(files));
    }
}


private JSONArray getFileArray(List<LspBaseFile> files)
{
   JSONArray arr = new JSONArray();
   for (LspBaseFile lbf : files) {
      JSONObject fobj = createJson("uri",lbf.getUri());
      arr.put(fobj);
    }
   return arr;
}


private JSONArray getFileArray(Map<LspBaseFile,LspBaseFile> files)
{
   JSONArray arr = new JSONArray();
   for (Map.Entry<LspBaseFile,LspBaseFile> ent : files.entrySet()) {
      LspBaseFile oldf = ent.getKey();
      LspBaseFile newf = ent.getValue();
      JSONObject fobj = createJson("oldUri",oldf.getUri(),"newUri",newf.getUri());
      arr.put(fobj);
    }
   return arr;
}




/********************************************************************************/
/*										*/
/*	Output methods								*/
/*										*/
/********************************************************************************/

void saveProject()
{
   try {
      File f1 = new File(base_directory,PROJECT_DATA_FILE);
      IvyXmlWriter xw = new IvyXmlWriter(f1);
      outputXml(xw);
      xw.close();
    }
   catch (IOException e) {
      LspLog.logE("Problem writing project file",e);
    }
}



void outputXml(IvyXmlWriter xw)
{
   xw.begin("PROJECT");
   xw.field("NAME",project_name);
   xw.field("LANGUAGE",project_language);
   xw.field("BASE",base_directory.getPath());
   for (LspBasePathSpec ps : project_paths) {
      ps.outputXml(xw);
    }
   for (LspBaseFile fd : project_files) {
      outputFile(fd,xw);
    }
// nobase_prefs.outputXml(xw);
   xw.end("PROJECT");
}




void outputProject(boolean files,boolean paths,boolean clss,boolean opts,IvyXmlWriter xw)
{
   if (xw == null) return;

   xw.begin("PROJECT");
   xw.field("NAME",project_name);
   xw.field("LANGUAGE",project_language);
   xw.field("PATH",base_directory.getPath());
   xw.field("WORKSPACE",project_manager.getWorkSpaceDirectory().getPath());

   if (paths) {
      xw.begin("CLASSPATH");
      for (LspBasePathSpec ps : project_paths) {
	 ps.outputXml(xw);
       }
      xw.end("CLASSPATH");
    }
   if (files) {
      for (LspBaseFile fd : project_files) {
	 outputFile(fd,xw);
       }
    }

   for (String s : project_references) {
      xw.textElement("REFERENCES",s);
    }
   for (LspBaseProject rp : getReferencingProjects()) {
      xw.textElement("USEDBY",rp.getName());
    }
   
   if (opts) {
      project_preferences.outputXml(xw); 
    }

   xw.end("PROJECT");
}



private void outputFile(LspBaseFile fd,IvyXmlWriter xw)
{
   xw.begin("FILE");
   xw.field("NAME",fd.getPath());
   xw.field("LANGUAGE",fd.getLanguage());
   xw.end("FILE");
}



/********************************************************************************/
/*										*/
/*	Edit parameters 							*/
/*										*/
/********************************************************************************/

void handleEditParameter(String bid,String name,String value)
{
   EditParameters ep = getParameters(bid);
   ep.setParameter(name,value);
}




private EditParameters getParameters(String id)
{
   synchronized (edit_parameters) {
      EditParameters ep = edit_parameters.get(id);
      if (ep == null) {
	 ep = new EditParameters();
	 edit_parameters.put(id,ep);
       }
      return ep;
    }
}



private static class EditParameters {

   private int delay_time;
   private boolean auto_elide;

   EditParameters() {
      delay_time = 250;
      auto_elide = false;
    }

   int getDelayTime()		{ return delay_time; }
   boolean getAutoElide()	{ return auto_elide; }

   void setParameter(String name,String value) {
      if (name.equals("AUTOELIDE")) {
         auto_elide = Boolean.parseBoolean(value);
       }
      else if (name.equals("ELIDEDELAY")) {
         delay_time = Integer.parseInt(value);
       }
    }

}	// end of inner class EditParamters






}	// end of class LspBaseProject




/* end of LspBaseProject.java */

