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
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

import edu.brown.cs.ivy.file.IvyFile;

class LspBaseLanguageData implements LspBaseConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private String language_name;
private String lspexec_string;
private String dapexec_string; 
private Map<String,Object> server_capabilities;
private JSONObject lsp_configuration;
private JSONObject client_configuration;
private JSONObject dap_configuration;
private boolean single_workspace;
private Set<String> file_extensions;
private File       debuy_file;



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

LspBaseLanguageData(String name,String exec,String dap,boolean single) {
   language_name = name;
   lspexec_string = exec;
   dapexec_string = dap;
   single_workspace = single;
   server_capabilities = new HashMap<>();
   file_extensions = null;
   lsp_configuration = null;
   client_configuration = null;
   dap_configuration = null;
   
   String rname = "lspbase-" + name + ".json";
   String json = null;
   try (InputStream ins = LspBaseMain.getResourceAsStream(rname)) {
      if (ins != null) json = IvyFile.loadFile(ins);
    }
   catch (IOException e) { }
   try {
      JSONObject cfg = new JSONObject(json);
      setCapabilities("lsp",cfg.getJSONObject("lspbaseConfiguration"));
      lsp_configuration = cfg.getJSONObject("initialConfiguration");
      client_configuration = cfg.getJSONObject("clientConfiguration");
      dap_configuration = cfg.getJSONObject("debugConfiguration");
    }
   catch (Throwable e) {
      LspLog.logE("Problem with capability json: ",e);
      System.exit(1);
    }
}


/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

String getName()                                { return language_name; }
String getLspExecString()                       { return lspexec_string; }
String getDapExecString()                       { return dapexec_string; }
boolean isSingleWorkspace()                     { return single_workspace; }

JSONObject getDebugConfiguration()              { return dap_configuration; }
JSONObject getLspConfiguration()                { return lsp_configuration; }
JSONObject getClientConfiguration()             { return client_configuration; }



/********************************************************************************/
/*                                                                              */
/*      Capabilities methods                                                    */
/*                                                                              */
/********************************************************************************/

void setCapabilities(String pfx,JSONObject caps)
{
   addJsonCapabilities(pfx,caps);
}



private void addJsonCapabilities(String pfx,JSONObject caps)
{
   if (caps == null) return;
   
   for (String key : caps.keySet()) {
      String vkey = (pfx == null ? key : pfx + "." + key);
      Object val = caps.get(key);
      if (val instanceof JSONObject) {
         addJsonCapabilities(vkey,(JSONObject) val);
       }
      else {
         server_capabilities.put(vkey,val);
       }
    }
}



Object getCapability(String key)
{
   return server_capabilities.get(key);
}


JSONArray getCapabilityArray(String key)
{
   Object cap = getCapability(key);
   if (cap == null || !(cap instanceof JSONArray)) return null;
   return (JSONArray) cap;
}


Set<String> getCapabilitySet(String key)
{
   Set<String> rslt = new HashSet<>();
   
   Object cap = getCapability(key);
   if (cap == null) return rslt;
   else if (cap instanceof JSONArray) {
      JSONArray arr = (JSONArray) cap;
      for (int i = 0; i < arr.length(); ++i) {
         rslt.add(arr.get(i).toString());
       }
    }
   else {
      rslt.add(cap.toString());
    }
   
   return rslt;
}


String getCapabilityString(String key)
{
   Object o = getCapability(key);
   if (o == null) return null;
   return o.toString();
}


boolean getCapabilityBool(String key)
{
   return getCapabilityBool(key,false);
}


boolean getCapabilityBool(String key,boolean dflt)
{
   Object o = getCapability(key);
   if (o == null) return dflt;
   if (o instanceof Boolean) return ((Boolean) o);
   return dflt;
}


int getCapabilityInt(String key)
{
   return getCapabilityInt(key,-1);
}


int getCapabilityInt(String key,int dflt)
{
   Object o = getCapability(key);
   if (o == null) return dflt;
   if (o instanceof Number) return ((Number) o).intValue();
   return dflt;
}


Set<String> getCapabilityStrings(String key)
{
   Set<String> rslt = new HashSet<>();
   Object o = getCapability(key);
   if (o != null) {
      if (o instanceof JSONArray) {
         JSONArray jarr = (JSONArray) o;
         for (int i = 0; i < jarr.length(); ++i) {
            String v = jarr.get(i).toString();
            rslt.add(v);
          }
       }
      else rslt.add(o.toString());
    }
   
   return rslt;
}



boolean isSourceFile(File path)
{
   if (file_extensions == null) {
      file_extensions = new HashSet<>();
      JSONArray arr = getCapabilityArray("lsp.extensions");
      for (Object o : arr) {
         file_extensions.add(o.toString());
       }
    }
   if (path.isDirectory()) return true;
   String name = path.getName();
   int idx = name.lastIndexOf(".");
   if (idx < 0) return false;
   String ext = name.substring(idx+1).toLowerCase();
   if (file_extensions.contains(ext)) return true;
   return false;
}



/********************************************************************************/
/*                                                                              */
/*      Output capabilities as preferences                                      */
/*                                                                              */
/********************************************************************************/

void addPreferences(String pfx,LspBasePreferences prefs)
{
   if (pfx == null) pfx = "";
   else if (!pfx.endsWith(".")) pfx += ".";
   
   for (Map.Entry<String,Object> ent : server_capabilities.entrySet()) {
      String vkey = pfx + ent.getKey();
      Object val = ent.getValue();
      addPreference(vkey,val,prefs);
    }
}


void addPreference(String pfx,Object val,LspBasePreferences prefs)
{
   if (val instanceof JSONObject) {
      JSONObject jval = (JSONObject) val;
      for (String key : jval.keySet()) {
         String vkey = (pfx == null ? key : pfx + "." + key);
         addPreference(vkey,jval.get(key),prefs);
       }
    }
   else if (val instanceof JSONArray) {
      JSONArray aval = (JSONArray) val;
      for (int i = 0; i < aval.length(); ++i) {
         String akey = (pfx == null ? "" + i : pfx + "." + i);
         addPreference(akey,aval.get(i),prefs);
       }
    }
   else {
      prefs.setProperty(pfx,val.toString());
    }
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
      return isSourceFile(path);
    }

}	// end of inner class SourceFilter

}       // end of class LspBaseLangaugeData




/* end of LspBaseLangaugeData.java */

