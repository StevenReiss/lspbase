/********************************************************************************/
/*                                                                              */
/*              LspBaseConstants.java                                           */
/*                                                                              */
/*      Bubbles back end that uses Language Server Protocol (LSP)               */
/*                                                                              */
/********************************************************************************/
/*      Copyright 2013 Brown University -- Steven P. Reiss                    */
/*********************************************************************************
 *  Copyright 2013, Brown University, Providence, RI.                            *
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
import java.net.URI;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONObject;

interface LspBaseConstants
{



/********************************************************************************/
/*                                                                              */
/*      Message server constants                                                */
/*                                                                              */
/********************************************************************************/

String LSPBASE_MINT_NAME = "BUBBLES_" + System.getProperty("user.name").replace(" ","_");

String PROJECT_DATA_FILE = ".lspproject";

/********************************************************************************/
/*										*/
/*	Logging constants							*/
/*										*/
/********************************************************************************/

enum LspBaseLogLevel {
   NONE,
   ERROR,
   WARNING,
   INFO,
   DEBUG
}




/********************************************************************************/
/*                                                                              */
/*      Callback classes                                                        */
/*                                                                              */
/********************************************************************************/

interface LspResponder {
   void handleResponse(Object data,JSONObject err);
}



interface LspNamer {
   void handleNames(LspBaseProject proj,LspBaseFile file,JSONArray names);
}


/********************************************************************************/
/*									        */
/*	Thread pool information 						*/
/*										*/
/********************************************************************************/

int LSPBASE_CORE_POOL_SIZE = 2;
int LSPBASE_MAX_POOL_SIZE = 8;
long LSPBASE_POOL_KEEP_ALIVE_TIME = 2*60*1000;



/********************************************************************************/
/*                                                                              */
/*      Editing Constants                                                       */
/*                                                                              */
/********************************************************************************/

String TOKEN_TYPES = "semanticTokensProvider.legend.tokenTypes";
String TOKEN_MODS = "semanticTokensProvider.legend.tokenModifiers";


class LineCol {
   private int line_number;
   private int col_number;
   
   LineCol(int line,int col) {
      line_number = line;
      col_number = col;
    }
   
   int getLine()                                { return line_number; }
   int getColumn()                              { return col_number; }
   
   int getLspLine()                             { return line_number - 1; }
   int getLspColumn()                           { return col_number - 1; }
   
}       // end of inner class LineCol


interface FindResult {
   JSONObject getRange();
   JSONObject getDefinition();
   LspBaseFile getFile();
}



/********************************************************************************/
/*                                                                              */
/*      Specification Decoding                                                  */
/*                                                                              */
/********************************************************************************/

String [] SymbolKinds = {
   "None", "File", "Module", "Namespace", "Package",
   "Class", "Method", "Property", "Field", "Constructor",
   "Enum", "Interface", "Function", "Variable", "Constant",
   "String", "Number", "Boolean", "Array", "Object",
   "Key", "Null", "EnumMember", "Struct", "Event",
   "Operator", "TypeParameter", "Local"
};

String [] CompletionKinds = {
      "OTHER", "OTHER", "METHOD_REF", "METHOD_REF", "METHOD_REF",
      "FIELD_REF", "LOCAL_VARIABLE_REF", "TYPE_REF", "TYPE_REF", "PACKAGE_REF",
      "FIELD_REF", "OTHER", "OTHER", "TYPE_REF", "KEYWORD", 
      "OTHER", "OTHER", "OTHER", "OTHER", "OTHER",
      "FIELD_REF", "OTHER", "TYPE_REF", "OTHER", "OTHER",
      "TYPE_REF"
};


String PRIVATE_PREFIX = "_private_buffer_";


/********************************************************************************/
/*                                                                              */
/*      Runtime constants                                                       */
/*                                                                              */
/********************************************************************************/

enum BreakType {
   NONE,
   LINE,
   EXCEPTION, 
   FUNCTION,
   DATA,
}


enum LspBaseConfigAttribute {
   NONE,
   PROJECT_ATTR,
   PROGRAM_ARGUMENTS,
   VM_ARGUMENTS,
   MAIN_TYPE,
   WORKING_DIRECTORY,
   ENCODING,
   NAME,
   FILE,
   DEBUG_LIBRARIES,
   TOOL_ARGS,
   CAPTURE_IN_FILE,
   ENVIRONMENT,
   CONNECT_MAP,
}

enum LspBaseDebugAction {
   NONE,
   TERMINATE,
   RESUME,
   STEP_INTO,
   STEP_OVER,
   STEP_RETURN,
   SUSPEND,
   DROP_TO_FRAME
}




String CONFIG_FILE = ".launches";
String BREAKPOINT_FILE = ".breakpoints";


class IdCounter {
   
   private int counter_value;
   
   IdCounter() {
      counter_value = 1;
    }
   
   synchronized public int nextValue() {
      return counter_value++;
    }
   
   synchronized public void noteValue(int v) {
      if (counter_value <= v) counter_value = v+1;
    }

}       // end of inner class IdCounter



/********************************************************************************/
/*                                                                              */
/*      Helper methods                                                          */
/*                                                                              */
/********************************************************************************/

public default String getUri(File f)
{
   URI u = f.toURI();
   String s = u.toString();
   if (!s.startsWith("file:///")) {
      s = "file:///" + s.substring(6);
    }
   
   return s;
}

public default String getUUID()
{
   return UUID.randomUUID().toString();
}


public default JSONObject createJson(Object ... params)
{
   JSONObject jo = new JSONObject();
   for (int i = 0; i < params.length-1; i += 2) {
      String key = params[i].toString();
      Object val = params[i+1];
      jo.put(key,val);
    }
   return jo;
}


public default JSONArray createJsonArray(Object ... elts)
{
   JSONArray jarr = new JSONArray();
   for (int i = 0; i < elts.length; ++i) {
      jarr.put(elts[i]);
    }
   return jarr;
}



}       // end of interface LspBaseConstants




/* end of LspBaseConstants.java */

