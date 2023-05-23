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
import java.util.ArrayList;
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
private File   base_file;
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

LspBaseLaunchConfig(String nm)
{
   config_name = nm;
   config_number = launch_counter.nextValue();
   config_id = "LAUNCH_" + Integer.toString(config_number);
   config_attrs = new HashMap<>();
   base_file = null;
   is_saved = false;
   working_copy = null;
   original_config = null;
}



LspBaseLaunchConfig(Element xml)
{
   config_name = IvyXml.getAttrString(xml,"NAME");
   config_number = IvyXml.getAttrInt(xml,"ID");
   launch_counter.noteValue(config_number);
   config_id = "LAUNCH_" + Integer.toString(config_number);
   
   config_attrs = new HashMap<LspBaseConfigAttribute,String>();
   for (Element ae : IvyXml.children(xml,"ATTR")) {
      LspBaseConfigAttribute attr = IvyXml.getAttrEnum(ae,"KEY",LspBaseConfigAttribute.NONE);
      config_attrs.put(attr,IvyXml.getAttrString(ae,"VALUE"));
    }
   String fn = IvyXml.getTextElement(xml,"FILE");
   if (fn == null) base_file = null;
   else base_file = new File(fn);
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
   base_file = orig.base_file;
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
   base_file = orig.base_file;
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
      base_file = working_copy.base_file;
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

public File getFileToRun()			{ return base_file; }
public void setFileToRun(File f) {
   base_file = f;
   setAttribute(LspBaseConfigAttribute.MAIN_TYPE,f.getPath());
}


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
   String s = config_attrs.get(LspBaseConfigAttribute.WD);
   if (s != null) {
      File f = new File(s);
      if (f.exists() && f.isDirectory()) return f;
    }
   
   try {
      String pnm = config_attrs.get(LspBaseConfigAttribute.PROJECT_ATTR);
      if (pnm == null) return null;
      LspBaseProject pp = LspBaseMain.getLspMain().getProjectManager().findProject(pnm);
      if (pp == null) return null;
      String files = config_attrs.get(LspBaseConfigAttribute.MAIN_TYPE);
//    File f2 = pp.findModuleFile(files);
//    File f3 = f2.getParentFile();
//    return f3;
    }
   catch (LspBaseException e) { }
   
   return null;
}


public String getEncoding()
{
   return config_attrs.get(LspBaseConfigAttribute.ENCODING);
}



public List<String> getCommandLine(LspBaseDebugManager dbg,int port) throws LspBaseException
{
   List<String> cmdargs = new ArrayList<>();
   
   String pnm = config_attrs.get(LspBaseConfigAttribute.PROJECT_ATTR);
   if (pnm == null) throw new LspBaseException("No project specified");
   LspBaseProject pp = LspBaseMain.getLspMain().getProjectManager().findProject(pnm);
   if (pp == null) throw new LspBaseException("Project " + pnm + " not found");
   
// File f1 = dbg.getNodeBinary();
// cmdargs.add(f1.getPath());
   // cmdargs.add("--inspect=" + port);
// cmdargs.add("--inspect-brk=" + port);
// addVmArgs(cmdargs);
// 
// String file = config_attrs.get(LspBaseConfigAttribute.MAIN_TYPE);
// String filename = config_attrs.get(LspBaseConfigAttribute.FILE);
// File f2 = null;
// if (f2 == null && filename != null) f2 = new File(filename);
// if (f2 != null) cmdargs.add(f2.getAbsolutePath());
// 
// addUserArgs(cmdargs);
// 
   return cmdargs;
}


private void addVmArgs(List<String> cmdargs)
{
   String args = config_attrs.get(LspBaseConfigAttribute.VM_ARGS);
   if (args == null || args.length() == 0) return;
   List<String> toks = IvyExec.tokenize(args);
   cmdargs.addAll(toks);
}



private void addUserArgs(List<String> cmdargs)
{
   String args = config_attrs.get(LspBaseConfigAttribute.ARGS);
   if (args == null || args.length() == 0) return;
   List<String> toks = IvyExec.tokenize(args);
   cmdargs.addAll(toks);
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
   xw.textElement("FILE",base_file);
   for (Map.Entry<LspBaseConfigAttribute,String> ent : config_attrs.entrySet()) {
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
   xw.field("NAME",config_name);
   xw.field("WORKING",!is_saved);
   xw.field("DEBUG",true);
   for (Map.Entry<LspBaseConfigAttribute,String> ent : config_attrs.entrySet()) {
      xw.begin("ATTRIBUTE");
      LspBaseConfigAttribute k = ent.getKey();
      xw.field("NAME",k);
      xw.field("TYPE","java.lang.String");
      xw.cdata(ent.getValue());
      xw.end("ATTRIBUTE");
    }
   xw.begin("TYPE");
   xw.field("NAME","JS");
   xw.end("TYPE");
   xw.end("CONFIGURATION");
}






}       // end of class LspBaseLaunchConfig




/* end of LspBaseLaunchConfig.java */

