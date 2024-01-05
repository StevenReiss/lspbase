/********************************************************************************/
/*                                                                              */
/*              LspBaseLaunchConfig.java                                        */
/*                                                                              */
/*      description of class                                                    */
/*                                                                              */
/********************************************************************************/
/*      Copyright 2011 Brown University -- Steven P. Reiss                    */
/*********************************************************************************
 *  Copyright 2011, Brown University, Providence, RI.                            *
 *                                                                               *
 *                        All Rights Reserved                                    *
 *                                                                               *
 * This program and the accompanying materials are made available under the      *
 * terms of the Eclipse Public License v1.0 which accompanies this distribution, *
 * and is available at                                                           *
 *      http://www.eclipse.org/legal/epl-v10.html                                *
 *                                                                               *
 ********************************************************************************/



package edu.brown.cs.bubbles.lspbase;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Element;

import edu.brown.cs.ivy.exec.IvyExec;
import edu.brown.cs.ivy.xml.IvyXml;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

class LspBaseLaunchConfig implements LspBaseConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private String config_name;
private String config_id;
private int	config_number;
private String  config_description;
private String  config_kind;
private LspBaseProject for_project;
private LspBaseLaunchConfig original_config;
private LspBaseLaunchConfig working_copy;
private boolean is_saved;
private Map<LspBaseConfigAttribute,String> config_attrs;

private static IdCounter launch_counter = new IdCounter();

// LOOK AT INITIALIZE AND ATTACH

/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

LspBaseLaunchConfig(LspBaseProject proj,String nm,String type,String kind)
{
   config_name = nm;
   config_number = launch_counter.nextValue();
   config_id = "LAUNCH_" + Integer.toString(config_number);
   config_description = type;
   config_kind = kind;
   config_attrs = new HashMap<>();
   is_saved = false;
   working_copy = null;
   original_config = null;
   for_project = proj;
}



LspBaseLaunchConfig(Element xml)
{
   config_name = IvyXml.getAttrString(xml,"NAME");
   config_number = IvyXml.getAttrInt(xml,"ID");
   config_kind = IvyXml.getAttrString(xml,"KIND");
   config_description = IvyXml.getAttrString(xml,"TYPE");
   launch_counter.noteValue(config_number);
   config_id = "LAUNCH_" + Integer.toString(config_number);
   String pnm = IvyXml.getAttrString(xml,"PROJECT_ATTR");
   LspBaseProjectManager pm = LspBaseMain.getLspMain().getProjectManager();
   try {
      for_project = pm.findProject(pnm);
    }
   catch (LspBaseException e) {
      for_project = null;
    }
   
   config_attrs = new HashMap<>();
   for (Element ae : IvyXml.children(xml,"ATTR")) {
      LspBaseConfigAttribute attr = IvyXml.getAttrEnum(ae,"KEY",LspBaseConfigAttribute.NONE);
      if (attr != LspBaseConfigAttribute.NONE) {
         config_attrs.put(attr,IvyXml.getAttrString(ae,"VALUE"));
       }
    }
   is_saved = true;
   working_copy = null;
   original_config = null;
}



LspBaseLaunchConfig(String nm,LspBaseLaunchConfig orig)
{
   config_name = nm;
   config_number = launch_counter.nextValue();
   config_id = "LAUNCH_" + Integer.toString(config_number);
   config_attrs = new HashMap<>(orig.config_attrs);
   config_kind = orig.config_kind;
   config_description = orig.config_description;
   for_project = orig.for_project;
   is_saved = false;
   working_copy = null;
   original_config = null;
}



LspBaseLaunchConfig(LspBaseLaunchConfig orig)
{
   config_name = orig.config_name;
   config_number = orig.config_number;
   config_id = orig.config_id;
   config_attrs = new HashMap<>(orig.config_attrs);
   config_kind = orig.config_kind;
   config_description = orig.config_description;
   for_project = orig.for_project;
   is_saved = false;
   working_copy = null;
   original_config = orig;
}




/********************************************************************************/
/*										*/
/*	Working copy methods							*/
/*										*/
/********************************************************************************/

LspBaseLaunchConfig getWorkingCopy()
{
   if (!is_saved) return this;
   if (working_copy == null) {
      working_copy = new LspBaseLaunchConfig(this);
    }
   return working_copy;
}


void commitWorkingCopy()
{
   if (!is_saved) {
      if (original_config != null) {
	 original_config.commitWorkingCopy();
       }
      else is_saved = true;
    }
   else if (working_copy != null) {
      config_name = working_copy.config_name;
      config_attrs = new HashMap<>(working_copy.config_attrs);
      working_copy = null;
    }
}



/********************************************************************************/
/*										*/
/*	Access methods								*/
/*										*/
/********************************************************************************/

public String getName() 			{ return config_name; }
String getId()					{ return config_id; }
void setName(String nm) 			{ config_name = nm; }
LspBaseProject getProject()                     { return for_project; }


void setAttribute(LspBaseConfigAttribute k,String v)
{
   if (k == null) return;
   
   if (v == null) config_attrs.remove(k);
   else config_attrs.put(k,v);
}

boolean isSaved()				{ return is_saved; }
void setSaved(boolean fg)			{ is_saved = fg; }

public String [] getEnvironment()		{ return null; }

public File getWorkingDirectory()
{
   String s = config_attrs.get(LspBaseConfigAttribute.WORKING_DIRECTORY);
   if (s != null) {
      File f = new File(s);
      if (f.exists() && f.isDirectory()) return f;
    }
   
   try {
      String pnm = config_attrs.get(LspBaseConfigAttribute.PROJECT_ATTR);
      if (pnm == null) return null;
      LspBaseProject pp = LspBaseMain.getLspMain().getProjectManager().findProject(pnm);
      if (pp == null) return null;
      String file = config_attrs.get(LspBaseConfigAttribute.FILE);
      if (file == null) file = config_attrs.get(LspBaseConfigAttribute.MAIN_TYPE);
      if (file != null) {
         LspBaseFile lbf = pp.findFile(file);
         return lbf.getFile().getParentFile();
       }
    }
   catch (LspBaseException e) { }
   
   return null;
}


public String getEncoding()
{
   return config_attrs.get(LspBaseConfigAttribute.ENCODING);
}

public File getFileToRun()
{
   String cn = config_attrs.get(LspBaseConfigAttribute.FILE);
   if (cn == null) {
      cn = config_attrs.get(LspBaseConfigAttribute.MAIN_TYPE);
    }
   if (cn == null) return null;
   return new File(cn);
}


public String getConnectMap()
{
   return config_attrs.get(LspBaseConfigAttribute.CONNECT_MAP);
}


public List<String> getProgramArguments()
{
   String s = config_attrs.get(LspBaseConfigAttribute.PROGRAM_ARGUMENTS);
   if (s == null) return null;
   List<String> args = IvyExec.tokenize(s);
   if (args.isEmpty()) return null;
   return args;
}


public List<String> getVMArguments()
{
   String s = config_attrs.get(LspBaseConfigAttribute.VM_ARGUMENTS);
   if (s == null) return null;
   List<String> args = IvyExec.tokenize(s);
   if (args.isEmpty()) return null;
   return args;
}


public List<String> getToolArguments()
{
   String s = config_attrs.get(LspBaseConfigAttribute.TOOL_ARGS);
   String d = config_attrs.get(LspBaseConfigAttribute.DEVICE);
   if (s == null && d == null) return null;
   List<String> args = IvyExec.tokenize(s);
   if (d != null) {
      args.add("-d");
      args.add(d);
    }
   if (args.isEmpty()) return null;
   return args;
}



/********************************************************************************/
/*										*/
/*	OutputMethods								*/
/*										*/
/********************************************************************************/

void outputSaveXml(IvyXmlWriter xw)
{
   xw.begin("LAUNCH");
   xw.field("NAME",config_name);
   xw.field("ID",config_number);
   xw.field("KIND",config_kind);
   xw.field("TYPE",config_description);  for (Map.Entry<LspBaseConfigAttribute,String> ent : config_attrs.entrySet()) {
      xw.begin("ATTR");
      xw.field("KEY",ent.getKey());
      xw.field("VALUE",ent.getValue());
      xw.end("ATTR");
    }
   xw.end("LAUNCH");
}


void outputBubbles(IvyXmlWriter xw)
{
   if (working_copy != null) {
      working_copy.outputBubbles(xw);
      return;
    }
   
   xw.begin("CONFIGURATION");
   xw.field("ID",config_id);
   xw.field("KIND",config_kind);
   xw.field("TYPE",config_description);
   xw.field("NAME",config_name);
   xw.field("WORKING",!is_saved);
   if (for_project != null) xw.field("PROJECT",for_project.getName());
   xw.field("DEBUG",true);
   for (Map.Entry<LspBaseConfigAttribute,String> ent : config_attrs.entrySet()) {
      xw.begin("ATTRIBUTE");
      LspBaseConfigAttribute k = ent.getKey();
      xw.field("NAME",k);
      xw.field("TYPE","java.lang.String");
      xw.cdata(ent.getValue());
      xw.end("ATTRIBUTE");
    }
   xw.end("CONFIGURATION");
}





}       // end of class LspBaseLaunchConfig




/* end of LspBaseLaunchConfig.java */

