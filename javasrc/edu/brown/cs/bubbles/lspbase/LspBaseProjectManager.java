/********************************************************************************/
/*										*/
/*		LspBaseProjectManager.java					*/
/*										*/
/*	Project manager -- handle all projects in workspace			*/
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

import org.json.JSONArray;
import org.w3c.dom.Element;

import edu.brown.cs.ivy.xml.IvyXml;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

class LspBaseProjectManager implements LspBaseConstants
{


/********************************************************************************/
/*										*/
/*	Private Storage 							*/
/*										*/
/********************************************************************************/

private LspBaseMain	lsp_main;
private File		work_space;
private Map<String,LspBaseProject> all_projects;
private LspBasePreferences system_preferences;

private static final String PROJECTS_FILE = ".projects";




/********************************************************************************/
/*										*/
/*	Constructors								*/
/*										*/
/********************************************************************************/

LspBaseProjectManager(LspBaseMain nm) throws LspBaseException
{
   lsp_main = nm;
   
   File ws = nm.getWorkSpaceDirectory();

   if (!ws.exists()) ws.mkdirs();
   if (!ws.exists() || !ws.isDirectory())
      throw new LspBaseException("Illegal work space specified: " + ws);

   work_space = ws;
   all_projects = new TreeMap<String,LspBaseProject>();

   File pf1 = new File(ws,".preferences");
   system_preferences = new LspBasePreferences(pf1);

   loadProjects();
}




/********************************************************************************/
/*										*/
/*	Setup methods								*/
/*										*/
/********************************************************************************/

LspBasePathSpec createPathSpec(Element xml)		{ return new LspBasePathSpec(xml); }

LspBasePathSpec createPathSpec(File src,boolean user,boolean exclude,boolean nest) {
   return new LspBasePathSpec(src,user,exclude,nest);
}



/********************************************************************************/
/*										*/
/*	Access methods								*/
/*										*/
/********************************************************************************/

LspBaseProject findProject(String proj) throws LspBaseException
{
   if (proj == null) return null;

   LspBaseProject pp = all_projects.get(proj);
   if (pp == null) throw new LspBaseException("Unknown project " + proj);

   return pp;
}



File getWorkSpaceDirectory()
{
   return lsp_main.getWorkSpaceDirectory();
}




private void forAllProjects(String proj,Consumer<LspBaseProject> f)
   throws LspBaseException
{
   if (proj != null) {
      LspBaseProject p = findProject(proj);
      f.accept(p);
    }
   else {
      for (LspBaseProject np : new ArrayList<>(all_projects.values())) {
	 f.accept(np);
       }
    }
}


LspBasePreferences getSystemPreferences()	
{
   return system_preferences;
}



/********************************************************************************/
/*										*/
/*	Command methods 							*/
/*										*/
/********************************************************************************/

void handleCommand(String cmd,String proj,Element xml,IvyXmlWriter xw)
   throws LspBaseException
{
   switch (cmd) {
      case "PROJECTS" :
	 handleListProjects(xw);
	 break;
      case "OPENPROJECT" :
	 handleOpenProject(proj,
	       IvyXml.getAttrBool(xml,"FILES",false),
	       IvyXml.getAttrBool(xml,"PATHS",false),
	       IvyXml.getAttrBool(xml,"CLASSES",false),
	       IvyXml.getAttrBool(xml,"OPTIONS",false),xw);
	 break;
      case "BUILDPROJECT" :
	 handleBuildProject(proj,
	       IvyXml.getAttrBool(xml,"CLEAN"),
	       IvyXml.getAttrBool(xml,"FULL"),
	       IvyXml.getAttrBool(xml,"REFRESH"),xw);
	 break;
      case "PATTERNSEARCH" :
	 handlePatternSearch(proj,IvyXml.getAttrString(xml,"PATTERN"),
	       IvyXml.getAttrString(xml,"FOR"),
	       IvyXml.getAttrBool(xml,"DEFS",true),
	       IvyXml.getAttrBool(xml,"REFS",true),
	       IvyXml.getAttrBool(xml,"SYSTEM",false),xw);
	 break;
      case "FINDBYKEY" :
      case "FINDDEFINITIONS" :
      case "FINDREFERENCES" :
      case "GETFULLYQUALIFIEDNAME" :
      case "FINDREGIONS" :
	 break;
      case "GETALLNAMES" :
	 handleGetAllNames(proj,IvyXml.getAttrString(xml,"BID","*"),
	       LspBaseMonitor.getSet(xml,"FILE"),
	       IvyXml.getAttrString(xml,"BACKGROUND"),xw);
	 break;
      case "PREFERENCES" :
	 handlePreferences(proj,xw);
	 break;
      case "SETPREFERENCES" :
	 Element pxml = IvyXml.getChild(xml,"profile");
	 if (pxml == null) pxml = IvyXml.getChild(xml,"OPTIONS");
	 handleSetPreferences(proj,pxml,xw);
	 break;
      case "COMMIT" :
         handleCommit(proj,IvyXml.getAttrString(xml,"BID","*"),
               IvyXml.getAttrBool(xml,"REFRESH",false),
               IvyXml.getAttrBool(xml,"SAVE",false),
               LspBaseMonitor.getElements(xml,"FILE"),xw);
          break;
      case "EDITPARAM" :
         handleEditParam(proj,IvyXml.getAttrString(xml,"BID","*"),
               IvyXml.getAttrString(xml,"NAME"),
               IvyXml.getAttrString(xml,"VALUE"));
         break;
      case "CREATEPROJECT" :
      case "EDITPROJECT" :
      case "CREATEPACKAGE" :
      case "FINDPACKAGE" :
      case "CREATECLASS" :
      default :
	 LspLog.logE("Unknown project command " + cmd);
	 break;
    }
}


void handleEditCommand(String cmd,String proj,Element xml,IvyXmlWriter xw)
   throws LspBaseException
{
   switch(cmd) {
      case "EDITPARAM" :
         handleEditParam(proj,IvyXml.getAttrString(xml,"BID","*"),
               IvyXml.getAttrString(xml,"NAME"),
               IvyXml.getAttrString(xml,"VALUE"));
         break;
      case "ELIDESET" :
         break;
      case "STARTFILE" :
         handleStartFile(proj,IvyXml.getAttrString(xml,"BID","*"),
               IvyXml.getAttrString(xml,"FILE"),xw);
         break;
      case "COMMIT" :
         handleCommit(proj,IvyXml.getAttrString(xml,"BID","*"),
               IvyXml.getAttrBool(xml,"REFRESH"),
               IvyXml.getAttrBool(xml,"SAVE"),
               LspBaseMonitor.getElements(xml,"FILES"),xw);
         break;
    }
}



 


/********************************************************************************/
/*										*/
/*	LIST PROJECTS command							*/
/*										*/
/********************************************************************************/

void handleListProjects(IvyXmlWriter xw)
{
   for (LspBaseProject p : all_projects.values()) {
      xw.begin("PROJECT");
      xw.field("NAME",p.getName());
      xw.field("LANGUAGE",p.getLanguage());
      xw.field("BASE",p.getBasePath().getPath());
      xw.end("PROJECT");
    }
}



/********************************************************************************/
/*										*/
/*	OPEN PROJECT command							*/
/*										*/
/********************************************************************************/

void handleOpenProject(String proj,boolean files,boolean paths,boolean classes,boolean opts,
      IvyXmlWriter xw) throws LspBaseException
{
   LspBaseProject p = all_projects.get(proj);
   if (p == null) throw new LspBaseException("Unknown project " + proj);

   p.open();

   if (xw != null) p.outputProject(files,paths,classes,opts,xw);
}



/********************************************************************************/
/*										*/
/*	BUILD PROJECT command							*/
/*										*/
/********************************************************************************/

void handleBuildProject(String proj,boolean clean,boolean full,boolean refresh,IvyXmlWriter xw)
   throws LspBaseException
{
   LspBaseProject p = all_projects.get(proj);
   if (p == null) throw new LspBaseException("Unknown project " + proj);

   p.build(refresh,true);
}



/********************************************************************************/
/*                                                                              */
/*      COMMIT command                                                          */
/*                                                                              */
/********************************************************************************/

void handleCommit(String proj,String bid,boolean refresh,boolean save,List<Element> files,IvyXmlWriter xw)
   throws LspBaseException
{
   xw.begin("COMMIT");
   forAllProjects(proj,
         (LspBaseProject p) -> p.commit(bid,refresh,save,files,xw));
   xw.end("COMMIT");
}




/********************************************************************************/
/*                                                                              */
/*      Editing commands                                                        */
/*                                                                              */
/********************************************************************************/

void handleEditParam(String proj,String bid,String name,String value)
   throws LspBaseException
{
   forAllProjects(proj,
         (LspBaseProject p) -> p.handleEditParameter(bid,name,value));
}



void handleStartFile(String proj,String bid,String file,IvyXmlWriter xw)
   throws LspBaseException
{
   LspBaseProject lbp = findProject(proj);
   if (lbp == null) throw new LspBaseException("Project " + proj + " not found");
   LspBaseFile lbf = lbp.findFile(file);
   if (lbf == null) throw new LspBaseException("File " + file + " not found for project " + proj);
   lbf.open();
}



/********************************************************************************/
/*										*/
/*	Get all names								*/
/*										*/
/********************************************************************************/

void handleGetAllNames(String proj,String bid,Set<String> files,String bkg,IvyXmlWriter xw)
   throws LspBaseException
{
   NameThread nt = new NameThread(bid,bkg,proj,xw);
   
   if (bkg != null) nt.start();
   else nt.compute();
}






private class NameThread extends Thread implements LspNamer {

   private String bump_id;
   private String name_id;
   private String for_project;
   private IvyXmlWriter xml_writer;

   NameThread(String bid,String nid,String proj,IvyXmlWriter xw) {
      super("LspBase_GetNames");
      bump_id = bid;
      name_id = nid;
      for_project = proj;
      xml_writer = (nid == null ? xw : null);
    }

   
   @Override public void handleNames(LspBaseProject project,LspBaseFile file,JSONArray names) {
      if (xml_writer == null) { 
         xml_writer = lsp_main.beginMessage("NAMES",bump_id);
         xml_writer.field("NID",name_id);
       }
        
      xml_writer.begin("FILE");
      xml_writer.textElement("PATH",file.getPath());
      for (int i = 0; i < names.length(); ++i) {
         LspBaseUtil.outputLspSymbol(project,file,names.getJSONObject(i),xml_writer);
       }
      xml_writer.end("FILE");
      
      if (name_id != null) {
         if (xml_writer.getLength() > 1000000) {
            lsp_main.finishMessageWait(xml_writer,15000);
            LspLog.logD("OUTPUT NAMES: " + xml_writer.toString());
            xml_writer = null;
          }
       }
    }
   
   
   @Override public void run() {
      LspLog.logD("START NAMES FOR " + name_id);
      try {
         compute();
       }
      catch (LspBaseException e) {
         LspLog.logE("Problem handling names",e);
       }
      if (name_id != null && xml_writer != null) {
         lsp_main.finishMessageWait(xml_writer);
         xml_writer = null;
       }
      if (name_id != null) {
         LspLog.logD("FINISH NAMES FOR " + name_id);
         IvyXmlWriter xw =  lsp_main.beginMessage("ENDNAMES",bump_id);
         xw.field("NID",name_id);
         lsp_main.finishMessage(xw);
       }
    }
   
    void compute() throws LspBaseException {
      forAllProjects(for_project,(LspBaseProject np) -> np.getAllNames(this));
    }
   
   
}	// end of inner class NameThread




/********************************************************************************/
/*										*/
/*	Pattern search								*/
/*										*/
/********************************************************************************/

void handlePatternSearch(String proj,String pat,String sf,
      boolean defs,boolean refs,boolean sys,IvyXmlWriter xw)
   throws LspBaseException
{
   forAllProjects(proj,(LspBaseProject np) ->  np.patternSearch(pat,sf,defs,refs,sys,xw));
}




/********************************************************************************/
/*										*/
/*	PREFERENCES and SETPREFERENCES commands 				*/
/*										*/
/********************************************************************************/

void handlePreferences(String proj,IvyXmlWriter xw)
   throws LspBaseException
{
   LspBasePreferences opts;
   if (proj == null) {
      opts = system_preferences;
    }
   else {
      LspBaseProject lspproj = findProject(proj);
      opts = lspproj.getPreferences();
    }

   opts.dumpPreferences(xw);
}



void handleSetPreferences(String proj,Element xml,IvyXmlWriter xw)
   throws LspBaseException
{
   forAllProjects(proj,(LspBaseProject np) -> setProjectPreferences(np,xml));
}


private boolean setProjectPreferences(LspBaseProject proj,Element xml)
{
   LspBasePreferences prefs;
   if (proj == null) prefs = getSystemPreferences();
   else prefs = proj.getPreferences();
   prefs.setPreferences(xml);

   return true;
}



/********************************************************************************/
/*										*/
/*	Handle loading projects 						*/
/*										*/
/********************************************************************************/

void loadProjects()
{
   File f = new File(work_space,PROJECTS_FILE);
   if (f.exists()) {
      Element xml = IvyXml.loadXmlFromFile(f);
      if (xml != null) {
	 for (Element pelt : IvyXml.children(xml,"PROJECT")) {
	    String nm = IvyXml.getAttrString(pelt,"NAME");
	    String pnm = IvyXml.getAttrString(pelt,"PATH");
	    File pf = new File(pnm);
	    if (pf.exists()) loadProject(nm,pf);
	  }
	 return;
       }
    }

   saveProjects();
}


void saveProjects()
{
   File f = new File(work_space,PROJECTS_FILE);
   try {
      IvyXmlWriter xw = new IvyXmlWriter(f);
      xw.begin("PROJECTS");
      for (LspBaseProject pp : all_projects.values()) {
	 xw.begin("PROJECT");
	 xw.field("NAME",pp.getName());
	 xw.field("PATH",pp.getBasePath().getPath());
	 xw.end("PROJECT");
       }
      xw.end("PROJECTS");
      xw.close();
    }
   catch (IOException e) {
      LspLog.logE("Problem writing project file",e);
    }
}


private void loadProject(String nm,File pf)
{
   LspBaseProject p = new LspBaseProject(lsp_main,this,nm,pf);

   all_projects.put(p.getName(),p);
}



}	// end of class LspBaseProjectManager




/* end of LspBaseProjectManager.java */

