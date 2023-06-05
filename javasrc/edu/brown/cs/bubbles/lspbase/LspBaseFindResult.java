/********************************************************************************/
/*                                                                              */
/*              LspBaseFindResult.java                                          */
/*                                                                              */
/*      Handle search results (FINDxxx)                                         */
/*                                                                              */
/********************************************************************************/
/*      Copyright 2011 Brown University -- Steven P. Reiss                    */
/*********************************************************************************
 *  Copyright 2011, Brown University, Providence, RI.                            *
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

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

class LspBaseFindResult implements LspBaseConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private LspBaseProject  for_project;
private LspBaseFile     start_file;
private boolean         find_defs;
private boolean         find_refs;
private boolean         find_impls;
private boolean         find_type;
private boolean         read_only;
private boolean         write_only;

private List<JSONObject> result_locs;



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

LspBaseFindResult(LspBaseProject proj,LspBaseFile file,
      boolean defs,boolean refs,boolean impl,boolean type,
      boolean ronly,boolean wonly)
{
   for_project = proj;
   start_file = file;
   find_defs = defs;
   find_refs = refs;
   find_impls = impl;
   find_type = type;
   read_only = ronly;
   write_only = wonly;
   result_locs = new ArrayList<>();
}



/********************************************************************************/
/*                                                                              */
/*      Access Methods                                                          */
/*                                                                              */
/********************************************************************************/

List<JSONObject> getResults()
{
   return result_locs;
}



/********************************************************************************/
/*                                                                              */
/*      Add/augment a location                                                  */
/*                                                                              */
/********************************************************************************/

void addResults(Object data,JSONObject err,String what)
{
   if (data == null) return;
   else if (data instanceof JSONArray) {
      JSONArray jarr = (JSONArray) data;
      for (int i = 0; i < jarr.length(); ++i) {
         addResults(jarr.getJSONObject(i),null,what);
       }   
    }
   else if (data instanceof JSONObject) {
      processResult((JSONObject) data,what);
    }
}


private void processResult(JSONObject data,String what)
{
   // data can be Location, LocationLink, DocumentHighlight
   LspBaseFile lbf = null;
   String uri = data.optString("uri",null);
   if (uri == null) {
      uri = data.optString("targetUri",null);
    }
   if (uri != null) {
      lbf = for_project.findFile(uri);
    }
   else {
      lbf = start_file;
    }
   if (lbf == null) return;
   
   JSONObject range = null;
   JSONObject srange = null;
   range = data.optJSONObject("range");
   if (range == null) {
      range = data.optJSONObject("targetRange");
      srange = data.optJSONObject("targetSelectionRange");
    }
   if (range == null) return;
   
   int kind = data.optInt("kind",0);
   
   int soffset = lbf.mapRangeToStartOffset(range);
   int eoffset = lbf.mapRangeToEndOffset(range);

   System.err.println("FOUND " + lbf.getPath() + " " + range + " " + kind + " " +
         soffset + " " + eoffset + " " + srange + " " + what);
}


}       // end of class LspBaseFindResult




/* end of LspBaseFindResult.java */

