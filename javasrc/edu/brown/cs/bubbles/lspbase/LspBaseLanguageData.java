/********************************************************************************/
/*                                                                              */
/*              LspBaseLangaugeData.java                                        */
/*                                                                              */
/*      Data for a language implementation                                      */
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
import java.io.FileFilter;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

class LspBaseLanguageData implements LspBaseConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private String language_name;
private String exec_string;
private Map<String,Object> server_capabilities;
private boolean single_workspace;
private Set<String> file_extensions;



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

LspBaseLanguageData(String name,String exec,String ext,boolean single) {
   language_name = name;
   exec_string = exec;
   single_workspace = single;
   server_capabilities = null;
   file_extensions = new HashSet<>();
   StringTokenizer tok = new StringTokenizer(ext,".");
   while (tok.hasMoreTokens()) {
      String extd = tok.nextToken();
      file_extensions.add(extd.toLowerCase());
    }
}


/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

String getName()                             { return language_name; }
String getExecString()                       { return exec_string; }
boolean isSingleWorkspace()                  { return single_workspace; }

void setCapabilities(Map<String,Object> caps) {
   if (server_capabilities == null) server_capabilities = caps;
   else server_capabilities.putAll(caps);
}



/********************************************************************************/
/*                                                                              */
/*      Check for source file                                                   */
/*                                                                              */
/********************************************************************************/

FileFilter getSourceFilter()
{
   return new SourceFilter();
}

private class SourceFilter implements FileFilter {

   @Override public boolean accept(File path) {
      if (path.isDirectory()) return true;
      String name = path.getName();
      int idx = name.lastIndexOf(".");
      if (idx < 0) return false;
      String ext = name.substring(idx+1).toLowerCase();
      if (file_extensions.contains(ext)) return true;
      return false;
    }

}	// end of inner class SourceFilter

}       // end of class LspBaseLangaugeData




/* end of LspBaseLangaugeData.java */

