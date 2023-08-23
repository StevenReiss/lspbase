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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

import org.json.JSONArray;
import org.json.JSONObject;
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
   all_projects = new TreeMap<>();

   File pf1 = new File(ws,".preferences");
   system_preferences = new LspBasePreferences(pf1);
   String s = lsp_main.getBaseLanguage();
   if (s != null) {
      LspBaseLanguageData ld = lsp_main.getLanguageData(s);
      ld.addPreferences("lspbase",system_preferences);
      system_preferences.flush();
    }

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
   if (proj.equals("*")) {
      for (LspBaseProject lp : all_projects.values()) {
         return lp;
       }
    }

   LspBaseProject pp = all_projects.get(proj);
   if (pp == null) {
      throw new LspBaseException("Unknown project " + proj);
    }

   return pp;
}


File getWorkSpaceDirectory()
{
   return lsp_main.getWorkSpaceDirectory();
}


Collection<LspBaseProject> getAllProjects() 
{
   return all_projects.values();
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



LspBaseFile findFile(String proj,String file)
{
   if (file == null) return null;
   if (file.startsWith("file:/")) {
      int j = 0;
      for (int i = 5; i < file.length(); ++i) {
	 if (file.charAt(i) == '/') j = i;
	 else break;
       }
      file = file.substring(j);
    }
   int idx0 = file.indexOf(PRIVATE_PREFIX);
   if (idx0 > 0) {
      int idx1 = file.lastIndexOf(".");
      String tail = (idx1 > 0 ? file.substring(idx1) : "");
      file = file.substring(0,idx0) + tail;
    }
   if (proj != null) {
      try {
	 LspBaseProject p = findProject(proj);
	 return p.findFile(file);
       }
      catch (LspBaseException e) {
	 return null;
       }
    }
   else {
      for (LspBaseProject np : all_projects.values()) {
	 LspBaseFile lbf = np.findFile(file);
	 if (lbf != null) return lbf;
       }
    }
   return null;
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
      case "FINDDEFINITIONS" :
         handleFindAll(proj,IvyXml.getAttrString(xml,"FILE"),
               IvyXml.getAttrInt(xml,"START"),IvyXml.getAttrInt(xml,"END"),
               IvyXml.getAttrBool(xml,"DEFS",true),
               IvyXml.getAttrBool(xml,"REFS",false),
               IvyXml.getAttrBool(xml,"IMPLS",false),
               IvyXml.getAttrBool(xml,"TYPE",false),
               false,false,xw);
         break;
         
      case "FINDREFERENCES" :
         handleFindAll(proj,IvyXml.getAttrString(xml,"FILE"),
               IvyXml.getAttrInt(xml,"START"),IvyXml.getAttrInt(xml,"END"),
               IvyXml.getAttrBool(xml,"DEFS",true),
               IvyXml.getAttrBool(xml,"REFS",true),
               IvyXml.getAttrBool(xml,"IMPLS",false),
               IvyXml.getAttrBool(xml,"TYPE",false),
               IvyXml.getAttrBool(xml,"RONLY",false),
               IvyXml.getAttrBool(xml,"WONLY",false),xw);
         break;
         
      case "GETFULLYQUALIFIEDNAME" :
         handleFullyQualifiedName(proj,IvyXml.getAttrString(xml,"FILE"),
               IvyXml.getAttrInt(xml,"START"),
               IvyXml.getAttrInt(xml,"END"),xw);
         break;
         
      case "FINDBYKEY" :
         handleFindByKey(proj,IvyXml.getAttrString(xml,"FILE"),
               IvyXml.getAttrString(xml,"KEY"),xw);
         break;
         
      case "FINDREGIONS" :
          handleFindRegions(proj,IvyXml.getAttrString(xml,"BID","*"),
               IvyXml.getAttrString(xml,"FILE"),
               IvyXml.getAttrString(xml,"CLASS"),
               IvyXml.getAttrBool(xml,"PREFIX",false),
               IvyXml.getAttrBool(xml,"STATICS",false),
               IvyXml.getAttrBool(xml,"COMPUNIT",false),
               IvyXml.getAttrBool(xml,"IMPORTS",false),
               IvyXml.getAttrBool(xml,"PACKAGE",false),
               IvyXml.getAttrBool(xml,"TOPDECLS",false),
               IvyXml.getAttrBool(xml,"FIELDS",false),
               IvyXml.getAttrBool(xml,"ALL",false),xw);
	 break;
         
      case "FINDPACKAGE" :
         handleFindPackage(proj,IvyXml.getAttrString(xml,"NAME"),xw);
         break;
         
      case "GETALLNAMES" :
	 handleGetAllNames(proj,IvyXml.getAttrString(xml,"BID","*"),
	       LspBaseMonitor.getSet(xml,"FILE"),
	       IvyXml.getAttrString(xml,"BACKGROUND"),xw);
	 break;
      case "PREFERENCES" :
	 handlePreferences(proj,IvyXml.getAttrString(xml,"LANG"),xw);
	 break;
      case "SETPREFERENCES" :
	 Element pxml = IvyXml.getChild(xml,"profile");
	 if (pxml == null) pxml = IvyXml.getChild(xml,"OPTIONS");
	 handleSetPreferences(proj,pxml,xw);
	 break;
      case "CREATEPROJECT" :
         handleCreateProject(IvyXml.getAttrString(xml,"NAME"),
               new File(IvyXml.getAttrString(xml,"DIR")),
               IvyXml.getAttrString(xml,"TYPE"),
               IvyXml.getChild(xml,"PROPS"),xw);
         break;
      case "EDITPROJECT" :
         handleEditProject(proj,
               IvyXml.getChild(xml,"PROJECT"),xw);
         break;

      case "CREATEPACKAGE" :
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
         handleElisionSetup(proj,IvyXml.getAttrString(xml,"BID","*"),
	       IvyXml.getAttrString(xml,"FILE"),
	       IvyXml.getAttrBool(xml,"COMPUTE",true),
	       LspBaseMonitor.getElements(xml,"REGION"),xw);
	 break;
      case "STARTFILE" :
	 handleStartFile(proj,IvyXml.getAttrString(xml,"BID","*"),
	       IvyXml.getAttrString(xml,"FILE"),xw);
	 break;
      case "COMMIT" :
	 handleCommit(proj,IvyXml.getAttrString(xml,"BID","*"),
	       IvyXml.getAttrBool(xml,"REFRESH"),
	       IvyXml.getAttrBool(xml,"SAVE"),
               IvyXml.getAttrBool(xml,"COMPILE"),
	       LspBaseMonitor.getElements(xml,"FILES"),xw);
	 break;
      case "EDITFILE" :
         handleEditFile(proj,IvyXml.getAttrString(xml,"BID","*"),
               IvyXml.getAttrString(xml,"FILE"),
               IvyXml.getAttrInt(xml,"ID"),
               LspBaseMonitor.getEditSet(xml),xw);
         break; 
      case "INDENT" :
         handleIndent(proj,IvyXml.getAttrString(xml,"BID","*"),
               IvyXml.getAttrString(xml,"FILE"),
               IvyXml.getAttrInt(xml,"ID"),
               IvyXml.getAttrInt(xml,"OFFSET"),
               IvyXml.getAttrBool(xml,"SPLIT"),xw);
         break;
      case "FIXINDENTS" :
         handleFixIndents(proj,IvyXml.getAttrString(xml,"BID","*"),
               IvyXml.getAttrString(xml,"FILE"),
               IvyXml.getAttrInt(xml,"ID"),
               IvyXml.getAttrInt(xml,"OFFSET"),xw);
         break;      
      case "GETCOMPLETIONS" :
         handleGetCompletions(proj,IvyXml.getAttrString(xml,"BID","*"),
               IvyXml.getAttrString(xml,"FILE"),
               IvyXml.getAttrInt(xml,"OFFSET"),xw);
         break;
      case "CREATEPRIVATE" :
         handleCreatePrivateBuffer(proj,IvyXml.getAttrString(xml,"BID","*"),
               IvyXml.getAttrString(xml,"PID"),
               IvyXml.getAttrString(xml,"FILE"),
               IvyXml.getAttrString(xml,"FROMPID"),xw);
         
         break;
      case "PRIVATEEDIT" :
         handlePrivateBufferEdit(proj,IvyXml.getAttrString(xml,"PID","*"),
               IvyXml.getAttrString(xml,"FILE"),
               LspBaseMonitor.getEditSet(xml),xw);
         
         break;
      case "REMOVEPRIVATE" :
         handleRemovePrivateBuffer(proj,
               IvyXml.getAttrString(xml,"PID"),
               IvyXml.getAttrString(xml,"FILE"));
         
         break;
      case "QUICKFIX" : 
         handleQuickFix(proj,IvyXml.getAttrString(xml,"BID","*"),
               IvyXml.getAttrString(xml,"FILE"),
               IvyXml.getAttrInt(xml,"OFFSET"),
               IvyXml.getAttrInt(xml,"LENGTH"),
               LspBaseMonitor.getElements(xml,"PROBLEM"),xw);
         break;
      case "FIXIMPORTS" :
         handleFixImports(proj,
               IvyXml.getAttrString(xml,"BID","*"),
               IvyXml.getAttrString(xml,"FILE"),
               IvyXml.getAttrInt(xml,"DEMAND",0),
               IvyXml.getAttrInt(xml,"STATICDEMAND",0),
               IvyXml.getAttrString(xml,"ORDER"),
               IvyXml.getAttrString(xml,"ADD"),xw);
         break;
      case "RENAME" :
         handleRename(proj,IvyXml.getAttrString(xml,"BID","*"),
               IvyXml.getAttrString(xml,"FILE"),
               IvyXml.getAttrInt(xml,"START"),
               IvyXml.getAttrInt(xml,"END"),
               IvyXml.getAttrString(xml,"NAME"),
               IvyXml.getAttrString(xml,"HANDLE"),
               IvyXml.getAttrString(xml,"NEWNAME"),
               IvyXml.getAttrBool(xml,"KEEPORIGINAL",false),
               IvyXml.getAttrBool(xml,"RENAMEGETTERS",false),
               IvyXml.getAttrBool(xml,"RENAMESETTERS",false),
               IvyXml.getAttrBool(xml,"UPDATEHIERARCHY",false),
               IvyXml.getAttrBool(xml,"UPDATEQUALIFIED",false),
               IvyXml.getAttrBool(xml,"UPDATEREFS",true),
               IvyXml.getAttrBool(xml,"UPDATESIMILAR",false),
               IvyXml.getAttrBool(xml,"UPDATETEXT",false),
               IvyXml.getAttrBool(xml,"DOEDIT",false),
               IvyXml.getAttrString(xml,"FILES"),xw);
         break;
      case "FORMATCODE" :
         handleFormatCode(proj,IvyXml.getAttrString(xml,"BID","*"),
	       IvyXml.getAttrString(xml,"FILE"),
	       IvyXml.getAttrInt(xml,"START"),
	       IvyXml.getAttrInt(xml,"END"),xw);
	 break;
      case "DELETE" :
         handleDeleteResource(proj,
               IvyXml.getAttrString(xml,"WHAT"),
               IvyXml.getAttrString(xml,"PATH"));
         
         break;
      case "RENAMERESOURCE" :
         handleRenameResource(proj,IvyXml.getAttrString(xml,"BID","*"),
               IvyXml.getAttrString(xml,"FILE"),
               IvyXml.getAttrString(xml,"NEWNAME"),xw);
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
/*										*/
/*	COMMIT command								*/
/*										*/
/********************************************************************************/

void handleCommit(String proj,String bid,
      boolean refresh,boolean save,boolean compile,
      List<Element> files,IvyXmlWriter xw)
   throws LspBaseException
{
   xw.begin("COMMIT");
   forAllProjects(proj,
	 (LspBaseProject p) -> p.commit(bid,refresh,save,compile,files,xw))         ;
   xw.end("COMMIT");
}



/********************************************************************************/
/*                                                                              */
/*      CREATEPROJECT  Command                                                  */
/*                                                                              */
/********************************************************************************/

void handleCreateProject(String name,File dir,String type,Element props,IvyXmlWriter xw)
      throws LspBaseException
{
   LspBaseProjectCreator creator = new LspBaseProjectCreator(name,dir,type,props);
   
   if (!creator.setupProject()) return;
   
   LspBaseProject lbp = addProject(name,dir);
   lbp.open();
   IvyXmlWriter msg = lsp_main.beginMessage("PROJECTOPEN");
   msg.field("PROJECT",name);
   lsp_main.finishMessage(msg);
   
   xw.begin("PROJECT");
   xw.field("NAME",name);
   xw.end("PROJECT");
}



/********************************************************************************/
/*                                                                              */
/*      EDITPROJECT command                                                     */
/*                                                                              */
/********************************************************************************/

void handleEditProject(String proj,Element data,IvyXmlWriter xw)
   throws LspBaseException
{
   LspBaseProject lbp = findProject(proj);
   if (lbp != null) {
      lbp.editProject(data);
    }
}



/********************************************************************************/
/*										*/
/*	Editing commands							*/
/*										*/
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
   lbf.open(bid);
}


void handleElisionSetup(String proj,String bid,String file,boolean compute,
      List<Element> regions,IvyXmlWriter xw)
   throws LspBaseException
{
   LspBaseFile lbf = findFile(proj,file);
   if (lbf == null) throw new LspBaseException("File " + file + " not found for project " + proj);
   LspBaseElider elider = lbf.getElider();
   if (regions != null) {
      elider.clearElideData();
      for (Element r : regions) {
	 int soff = IvyXml.getAttrInt(r,"START");
	 int eoff = IvyXml.getAttrInt(r,"END");
	 if (soff < 0 || eoff < 0) throw new LspBaseException("Missing start or end offset for elision region");
	 elider.addElideRegion(soff,eoff);
       }
    }
  if (compute) {
     xw.begin("ELISION");
     if (elider != null) elider.computeElision(xw);
     xw.end("ELISION");
   }
  else {
     xw.emptyElement("SUCCESS");
   }
}


void handleEditFile(String proj,String bid,String file,int eid,
      List<LspBaseEdit> edits,IvyXmlWriter xw)
   throws LspBaseException
{
   LspBaseFile lbf = findFile(proj,file);
   if (lbf == null) throw new LspBaseException("File " + file + " not found for project " + proj);
   lbf.edit(bid,eid,edits);
   LspLog.logD("DONE EDIT");
   xw.emptyElement("SUCCESS");
}



void handleIndent(String proj,String bid,String file,int eid,int offset,boolean split,IvyXmlWriter xw)
   throws LspBaseException
{
   LspBaseFile lbf = findFile(proj,file);
   if (lbf == null) throw new LspBaseException("File " + file + " not found for project " + proj);
   lbf.computeIndent(bid,eid,offset,split,xw);
}



void handleFixIndents(String proj,String bid,String file,int eid,int offset,IvyXmlWriter xw)
   throws LspBaseException
{
   LspBaseFile lbf = findFile(proj,file);
   if (lbf == null) throw new LspBaseException("File " + file + " not found for project " + proj);
   lbf.fixIndents(bid,eid,offset,xw);
}



void handleGetCompletions(String proj,String bid,String file,int offset,IvyXmlWriter xw)
   throws LspBaseException
{
   LspBaseFile lbf = findFile(proj,file);
   if (lbf == null) throw new LspBaseException("File " + file + " not found for project " + proj);
   lbf.getCompletions(bid,offset,xw);
}


void applyWorkspaceEdit(JSONObject wsedit) throws LspBaseException
{
   JSONArray chngs = wsedit.getJSONArray("documentChanges");
   for (int i = 0; i < chngs.length(); ++i) {
      JSONObject chng = chngs.getJSONObject(i);
      String kind = chng.optString("kind","edit");
      switch (kind) {
         case "create" :
            // TODO: handle create a file
            break;
         case "rename" :
            // TODO: handle rename a file
            break;
         case "delete" :
            // TODO: handle delete a file
            break;
         case "edit" :
            applyTextDocumentEdit(chng);
            break;
       }
    }
}


void applyTextDocumentEdit(JSONObject tdedit) throws LspBaseException
{
   JSONObject tdoc = tdedit.getJSONObject("textDocument");
   LspBaseFile lbf = findFile(null,tdoc.getString("uri"));
   int tid = tdoc.optInt("version",0);
   JSONArray jedits = tdedit.getJSONArray("edits");
   lbf.edit("*APPLY*",tid,jedits);
}


/********************************************************************************/
/*                                                                              */
/*      Handle FIXIMPORTS                                                       */
/*                                                                              */
/********************************************************************************/

void handleFixImports(String proj,String bid,String file,
      int demand,int staticdemand,String order,String add,
      IvyXmlWriter xw)
   throws LspBaseException
{
   LspBaseFile lbf = findFile(proj,file);
   if (lbf == null) throw new LspBaseException("File " + file + " not found for project " + proj);
   lbf.fixImports(bid,demand,staticdemand,order,add,xw);
}



/********************************************************************************/
/*                                                                              */
/*      Handle QUICKFIX                                                         */
/*                                                                              */
/********************************************************************************/

void handleQuickFix(String proj,String bid,String file,int offset,int length,
      List<Element> problems,IvyXmlWriter xw)
   throws LspBaseException
{
   LspBaseFile lbf = findFile(proj,file);
   if (lbf == null) throw new LspBaseException("File " + file + " not found for project " + proj);
   lbf.getCodeActions(bid,offset,length,problems,xw);
}


/********************************************************************************/
/*                                                                              */
/*      Handle RENAME                                                           */
/*                                                                              */
/********************************************************************************/

void handleRename(String proj,String bid,String file,
      int soffset,int eoffset,
      String name,String handle,String newname,
      boolean keeporig,boolean getters,boolean setters,
      boolean hier,boolean qualified,boolean refs,
      boolean similar,boolean text,boolean doedit,
      String files,IvyXmlWriter xw)
   throws LspBaseException
{
   LspBaseFile lbf = findFile(proj,file);
   if (lbf == null) throw new LspBaseException("File " + file + " not found for project " + proj);
   lbf.rename(soffset,eoffset,name,handle,newname,keeporig,getters,setters,
         hier,qualified,refs,similar,text,doedit,files,xw);
}



/********************************************************************************/
/*                                                                              */
/*      Handle FORMATCODE                                                       */
/*                                                                              */
/********************************************************************************/

void handleFormatCode(String proj,String bid,String file,
      int start,int end,IvyXmlWriter xw)
   throws LspBaseException
{
   LspBaseFile lbf = findFile(proj,file);
   if (lbf == null) throw new LspBaseException("File " + file + " not found for project " + proj);
   lbf.formatCode(bid,start,end,xw);
}


/********************************************************************************/
/*                                                                              */
/*      Handle DELETE (resource)                                                */
/*                                                                              */
/********************************************************************************/

void handleDeleteResource(String proj,String what,String path)
      throws LspBaseException
{
   if (what.equals("PROJECT")) {
      // TODO: handle delete project
    }
   else {
      LspBaseProject lbp = findProject(proj);
      lbp.handleDeleteResource(what,path);
    }
}


/********************************************************************************/
/*                                                                              */
/*      Handle RENAMERESOURCE                                                   */
/*                                                                              */
/********************************************************************************/

void handleRenameResource(String proj,String bid,String file,String newname,IvyXmlWriter xw)
      throws LspBaseException
{
   LspBaseProject lbp = findProject(proj);
   lbp.handleRenameResource(bid,file,newname,xw);
}




/********************************************************************************/
/*                                                                              */
/*      Private buffer commands                                                 */
/*                                                                              */
/********************************************************************************/

void handleCreatePrivateBuffer(String proj,String bid,String pid,String file,String frompid,
      IvyXmlWriter xw)
   throws LspBaseException
{
   LspBaseFile lbf = findFile(proj,file);
   if (lbf == null) throw new LspBaseException("File " + file + " not found for project " + proj);
   lbf.createPrivateBuffer(bid,pid,frompid,xw);
}



void handlePrivateBufferEdit(String proj,String pid,String file,List<LspBaseEdit> edits,IvyXmlWriter xw)
   throws LspBaseException
{
   LspBaseFile lbf = findFile(proj,file);
   if (lbf == null) throw new LspBaseException("File " + file + " not found for project " + proj);
   lbf.editPrivateBuffer(pid,edits,xw);
}


void handleRemovePrivateBuffer(String proj,String pid,String file)
   throws LspBaseException
{
   LspBaseFile lbf = findFile(proj,file);
   if (lbf == null) throw new LspBaseException("File " + file + " not found for project " + proj);
   lbf.removePrivateBuffer(pid);
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
         JSONObject jobj = names.getJSONObject(i);
         LspBaseUtil.outputLspSymbol(project,file,jobj,xml_writer);
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
   forAllProjects(proj,(LspBaseProject np) ->  np.safePatternSearch(pat,sf,defs,refs,sys,xw));
}






/********************************************************************************/
/*                                                                              */
/*      Find References/definitions                                             */
/*                                                                              */
/********************************************************************************/

void handleFindAll(String proj,String file,int start,int end,
      boolean defs,boolean refs,boolean impls,boolean type,boolean ronly,
      boolean wonly,IvyXmlWriter xw)
   throws LspBaseException
{
   LspBaseFile lbf = findFile(proj,file);
   if (lbf == null) throw new LspBaseException("File " + file + " not found");
   
   lbf.getProject().findAll(lbf,start,end,defs,refs,impls,type,ronly,wonly,xw);
}


/********************************************************************************/
/*                                                                              */
/*      Fully Qualified Name Query                                              */
/*                                                                              */
/********************************************************************************/

void handleFullyQualifiedName(String proj,String file,int start,int end,IvyXmlWriter xw)
   throws LspBaseException
{
   LspBaseFile lbf = findFile(proj,file);
   if (lbf == null) throw new LspBaseException("File " + file + " not found");
   
   lbf.getProject().fullyQualifiedName(lbf,start,end,xw);
}


/********************************************************************************/
/*                                                                              */
/*      Find by Key query                                                       */
/*                                                                              */
/********************************************************************************/

void handleFindByKey(String proj,String file,String key,IvyXmlWriter xw)
   throws LspBaseException
{
   LspBaseFile lbf = findFile(proj,file);
   if (lbf == null) throw new LspBaseException("File " + file + " not found");
   
   lbf.getProject().findByKey(lbf,key,xw);
}



/********************************************************************************/
/*                                                                              */
/*      Find Regions query                                                      */
/*                                                                              */
/********************************************************************************/

void handleFindRegions(String proj,String bid,String file,String cls,
      boolean prefix,boolean statics,boolean compunit,boolean imports,
      boolean pkg,boolean topdecls,boolean fields,boolean all,
      IvyXmlWriter xw)
   throws LspBaseException
{
   if (file == null) {
      // need to find file given class
      throw new LspBaseException("File must be given for FINDREGIONS");
    }
   
   LspBaseFile lbf = findFile(proj,file);
   if (lbf == null) {
      throw new LspBaseException("File " + file + " not found");
    }
   
   lbf.findRegions(cls,prefix,statics,compunit,imports,pkg,topdecls,fields,all,xw);
}



/********************************************************************************/
/*                                                                              */
/*      FINDPACKAGE command                                                     */
/*                                                                              */
/********************************************************************************/

void handleFindPackage(String proj,String name,IvyXmlWriter xw)
   throws LspBaseException
{
   LspLog.logD("NEED TO FIND PACKAGE FOR " + name + " in " + proj);
   LspBaseProject lbp = findProject(proj);
   if (lbp == null) return;
   
}



/********************************************************************************/
/*										*/
/*	PREFERENCES and SETPREFERENCES commands 				*/
/*										*/
/********************************************************************************/

void handlePreferences(String proj,String lang,IvyXmlWriter xw)
   throws LspBaseException					
{
   LspBasePreferences opts;
   if (proj == null) {
      opts = system_preferences;
      if (lang != null) {
         if (lang.endsWith("Lsp")) {
            lang = lang.substring(0,lang.length()-3);
          }
         if (!lang.equalsIgnoreCase(lsp_main.getBaseLanguage())) {
            LspBaseLanguageData ld = lsp_main.getLanguageData(lang);
            opts = addLanguageOptions(opts,ld);
          }
       }
    }
   else {
      LspBaseProject lspproj = findProject(proj);
      ensureInitialized(lspproj);
      opts = lspproj.getPreferences();
      opts = addLanguageOptions(opts,lspproj.getLanguageData());
    }

   opts.dumpPreferences(xw);
}


private LspBasePreferences addLanguageOptions(LspBasePreferences opts,LspBaseLanguageData ld)
{
   if (ld != null) {
      opts = new LspBasePreferences(opts);
      ld.addPreferences("lspbase",opts);
      opts.setProperty("LaunchConfigurations","lspbase.lsp.launchConfigurations");
    }
   
   return opts;
}


void ensureInitialized(LspBaseProject p) 
{
   try {
      if (!p.isOpen()) p.open();
      p.getProtocol().initialize();
    }
   catch (LspBaseException e) { }
}



void handleSetPreferences(String proj,Element xml,IvyXmlWriter xw)
   throws LspBaseException
{
   if (proj == null) {
      setProjectPreferences(null,xml);
    }
   else {
      LspBaseProject bp = findProject(proj);
      setProjectPreferences(bp,xml);
    }
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
   LspBaseProject first = null;
   
   File f = new File(work_space,PROJECTS_FILE);
   if (f.exists()) {
      Element xml = IvyXml.loadXmlFromFile(f);
      if (xml != null) {
	 for (Element pelt : IvyXml.children(xml,"PROJECT")) {
	    String nm = IvyXml.getAttrString(pelt,"NAME");
	    String pnm = IvyXml.getAttrString(pelt,"PATH");
	    File pf = new File(pnm);
	    if (pf.exists()) {
               LspBaseProject bp = loadProject(nm,pf);
               if (first == null) first = bp;
             }
	  }
         return;
       }
    }

   saveProjects();
}


LspBaseProject addProject(String name,File dir) 
{
   LspBaseProject lbp = loadProject(name,dir);
   saveProjects();
   return lbp;
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


private LspBaseProject loadProject(String nm,File pf)
{
   LspBaseProject p = new LspBaseProject(lsp_main,this,nm,pf);

   all_projects.put(p.getName(),p);
   
   return p;
}



}	// end of class LspBaseProjectManager




/* end of LspBaseProjectManager.java */

