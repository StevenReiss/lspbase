/********************************************************************************/
/*										*/
/*		LspBasePathSpec.java						*/
/*										*/
/*	Path specification							*/
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
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.w3c.dom.Element;

import edu.brown.cs.ivy.xml.IvyXml;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

class LspBasePathSpec implements LspBaseConstants
{


/********************************************************************************/
/*										*/
/*	Private Storage 							*/
/*										*/
/********************************************************************************/

private File directory_file;
private boolean is_user;
private boolean is_nested;
private String version_info;
private int path_id;
private Set<String> exclude_patterns;
private Set<String> include_patterns;



/********************************************************************************/
/*										*/
/*	Constructors								*/
/*										*/
/********************************************************************************/

LspBasePathSpec(Element xml)
{
   String fnm = IvyXml.getTextElement(xml,"SOURCE");
   if (fnm == null) fnm = IvyXml.getTextElement(xml,"BINARY");
   if (fnm == null) {
      // old form
      fnm = IvyXml.getTextElement(xml,"DIR") ;
      is_user = IvyXml.getAttrBool(xml,"USER");
    }
   else {
      is_user = true;
      String typ = IvyXml.getAttrString(xml,"TYPE","USER");
      switch (typ) {
	 case "LIBRARY" :
	    is_user = false;
	    break;
	 default :
       }
    }
   include_patterns = new LinkedHashSet<>();
   for (Element e : IvyXml.children(xml,"INCLUDE")) {
      String s = IvyXml.getAttrString(e,"PATH");
      include_patterns.add(s);
    }
   exclude_patterns = new LinkedHashSet<>();
   for (Element e : IvyXml.children(xml,"EXCLUDE")) {
      String s = IvyXml.getAttrString(e,"PATH");
      exclude_patterns.add(s);
    }
   version_info = IvyXml.getTextElement(xml,"VERSION");
   directory_file = new File(fnm);
   is_nested = IvyXml.getAttrBool(xml,"NESTED");
   path_id = IvyXml.getAttrInt(xml,"ID",0);
}



LspBasePathSpec(File f,boolean u,boolean e,boolean n)
{
   directory_file = f;
   is_user = u;
   is_nested = n;
   path_id = 0;
   include_patterns = new LinkedHashSet<>();
   exclude_patterns = new LinkedHashSet<>();
}


LspBasePathSpec(String lib,String version)
{
   directory_file = new File(lib);
   is_user = false;
   is_nested = false;
   version_info = version;
   path_id = 0;
   include_patterns = new LinkedHashSet<>();
   exclude_patterns = new LinkedHashSet<>();
}


void updateFrom(LspBasePathSpec nspec)
{
   is_user = nspec.is_user;
   is_nested = nspec.is_nested;
   version_info = nspec.version_info;
   if (path_id == 0) path_id = nspec.path_id;
   include_patterns = new LinkedHashSet<>(nspec.include_patterns);
   exclude_patterns = new LinkedHashSet<>(nspec.exclude_patterns);
}



/********************************************************************************/
/*										*/
/*	AccessMethods								*/
/*										*/
/********************************************************************************/

File getFile()				{ return directory_file; }

String getInfo()			{ return version_info; }

boolean isUser()			{ return is_user; }

boolean isNested()			{ return is_nested; }

int getId()
{
   if (path_id == 0) path_id = hashCode();
   return path_id;
}


void setProperties(boolean usr,boolean exc,boolean nest)
{
   is_user = usr;
   is_nested = nest;
}

Collection<String> getExcludes()	{ return exclude_patterns; }
Collection<String> getIncludes()	{ return include_patterns; }


/********************************************************************************/
/*										*/
/*	Matching methods							*/
/*										*/
/********************************************************************************/

boolean useFile(File path)
{
   boolean incl = true;
   for (String s : exclude_patterns) {
      if (match(s,path)) incl = false;
    }
   if (!incl) {
      for (String s : include_patterns) {
	 if (match(s,path)) incl = true;
       }
    }
   return incl;
}



private boolean match(String pat,File path)
{
   File f = new File(pat);
   if (path == null) return false;
   else if (!f.isAbsolute()) {
      String par = f.getParent();
      if (par == null || pat.equals("*") || pat.equals("**")) {
	 return path.getName().equals(f.getName());
       }
    }
   else if (path.equals(f)) return true;

   return false;
}




/********************************************************************************/
/*										*/
/*	Output methods								*/
/*										*/
/********************************************************************************/

public void outputXml(IvyXmlWriter xw)
{
   xw.begin("PATH");
   if (path_id == 0) path_id = hashCode();
   xw.field("ID",path_id);
   xw.field("SOURCE",directory_file.getPath());
   if (!is_user) xw.field("TYPE","LIBRARY");
   else xw.field("TYPE","SOURCE");
   xw.field("NESTED",is_nested);
   if (version_info != null) xw.field("VERSION",version_info);
   for (String p : include_patterns) {
      xw.begin("INCLUDE");
      xw.field("PATH",p);
      xw.end("INCLUDE");
    }
   for (String p : exclude_patterns) {
      xw.begin("EXCLUDE");
      xw.field("PATH",p);
      xw.end("EXCLUDE");
    }
   xw.end("PATH");
}


@Override public String toString()
{
   String rslt = directory_file.getPath();
   if (is_user) rslt += "@";
   if (is_nested) rslt += "^";
   return rslt;
}

}	// end of class LspBasePathSpec




/* end of LspBasePathSpec.java */















