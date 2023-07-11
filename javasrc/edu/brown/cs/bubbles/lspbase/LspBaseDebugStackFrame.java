/********************************************************************************/
/*                                                                              */
/*              LspBaseDebugStackFrame.java                                     */
/*                                                                              */
/*      Representation of a stack frame                                         */
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

import org.json.JSONObject;

import edu.brown.cs.ivy.xml.IvyXmlWriter;

class LspBaseDebugStackFrame implements LspBaseConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private int     frame_index;
private int     frame_id;
private String  frame_method;
private File    frame_file;
private int     frame_line;
private int     frame_column;
private boolean is_synthetic;



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

LspBaseDebugStackFrame(int idx,JSONObject sfobj)
{
   frame_index = idx;
   frame_id = sfobj.getInt("id");
   frame_method = sfobj.getString("name");
   frame_file = null;
   JSONObject src = sfobj.optJSONObject("source");
   if (src != null) {
      String path = sfobj.optString("path",null);
      if (path != null) frame_file = new File(path);
   }
   frame_line = sfobj.getInt("line");
   frame_column = sfobj.getInt("column");
   is_synthetic = false;
   String hint = sfobj.optString("presentationHint","normal");
   if (hint.equals("label")) is_synthetic = true;
   
   // need to get variables
}




/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

int getIndex()                  { return frame_index; }



/********************************************************************************/
/*                                                                              */
/*      Output methods                                                          */
/*                                                                              */
/********************************************************************************/

void outputXml(IvyXmlWriter xw,int ctr,int depth)
{
   xw.begin("STACKFRAME");
   xw.field("LEVEL",frame_index);
   xw.field("NAME",frame_method);
   xw.field("ID",frame_id);
   xw.field("LINENO",frame_line);
   xw.field("COLNO",frame_column);
   if (frame_file != null) xw.field("FILE",frame_file.getPath());
   if (is_synthetic) xw.field("SYNTHETIC",true);
   
   xw.end("STACKFRAME");
}


}       // end of class LspBaseDebugStackFrame




/* end of LspBaseDebugStackFrame.java */

