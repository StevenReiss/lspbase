/********************************************************************************/
/*                                                                              */
/*              LspBaseEdit.java                                                */
/*                                                                              */
/*      Information about an edit                                               */
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

import java.util.concurrent.atomic.AtomicInteger;

import org.json.JSONObject;
import org.w3c.dom.Element;

import edu.brown.cs.ivy.xml.IvyXml;

class LspBaseEdit implements LspBaseConstants, Comparable<LspBaseEdit>
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private int start_offset;
private int end_offset;
private String edit_text;
private int edit_index;

private static AtomicInteger edit_counter = new AtomicInteger(0);


/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

LspBaseEdit(Element e) {
   start_offset = IvyXml.getAttrInt(e,"START");
   end_offset = IvyXml.getAttrInt(e,"END",start_offset);
   edit_text = IvyXml.getText(e);
   if (IvyXml.getAttrBool(e,"ENCODE")) {
      edit_text = new String(IvyXml.stringToByteArray(edit_text));
    }
   if (edit_text != null && edit_text.length() == 0) edit_text = null;
   edit_index = edit_counter.incrementAndGet();
}



LspBaseEdit(LspBaseFile file,JSONObject edit)
{
   JSONObject range = edit.getJSONObject("range");
   start_offset = file.mapRangeToStartOffset(range);
   end_offset = file.mapRangeToEndOffset(range);
   edit_text = edit.optString("newText",null);
   if (edit_text == null) edit_text = edit.optString("text",null);
   if (edit_text != null && edit_text.length() == 0) edit_text = null;
   edit_index = edit_counter.incrementAndGet();
}



/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

int getOffset()                                 { return start_offset; }
int getLength()                                 { return end_offset - start_offset; }
String getText()                                { return edit_text; }

@Override public int compareTo(LspBaseEdit ed)
{
   if (start_offset < ed.start_offset) return 1;
   else if (start_offset > ed.start_offset) return -1;
   if (end_offset < ed.end_offset) return 1;
   else if (end_offset > ed.end_offset) return -1;
   return edit_index - ed.edit_index;
}



}       // end of class LspBaseEdit




/* end of LspBaseEdit.java */

