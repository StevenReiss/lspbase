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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.text.Segment;

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

private Map<RefResult,RefResult> result_locs;




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
   result_locs = new HashMap<>();
}



/********************************************************************************/
/*                                                                              */
/*      Access Methods                                                          */
/*                                                                              */
/********************************************************************************/

List<FindResult> getResults()
{
   return new ArrayList<>(result_locs.keySet());
}



/********************************************************************************/
/*                                                                              */
/*      Add/augment a location                                                  */
/*                                                                              */
/********************************************************************************/

void addResults(JSONArray jarr,String what)
{
   LspLog.logD("Add array results " + jarr.length());
   
   for (int i = 0; i < jarr.length(); ++i) {
      processResult(jarr.getJSONObject(i),what);
    }   
}


void addResult(JSONObject data,String what)
{
   processResult( data,what);
}


void addResults(Object data,String what)
{
   if (data == null) return;
   else if (data instanceof JSONArray) addResults((JSONArray) data,what);
   else if (data instanceof JSONObject) addResult((JSONObject) data,what);
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
   // if DEFS and lbf is null bu uri is not null, consider finding def in that file
   // using a documentSymbols search at this point
   
   if (lbf == null) return;
   JSONObject range = data.optJSONObject("range");
   if (range != null && what.equals("REFS")) {
      int off0 = lbf.mapRangeToStartOffset(range);
      int off1 = lbf.mapRangeToEndOffset(range);
      if (off1 > off1) {
         Segment seg = lbf.getSegment(off0,off1-off0);
         int delta = 0;
         while (delta < seg.length() && !Character.isJavaIdentifierStart(seg.charAt(delta))) {
            ++delta;
          }
         if (delta > 0) {
            JSONObject rangestart = range.getJSONObject("start");
            int cpos = rangestart.getInt("character");
            rangestart.put("character",cpos+delta);
          }
       }
    }
   
   JSONObject def = null;
   JSONObject cont = null;
   JSONObject defrange = null;
   range = data.optJSONObject("range");
   if (range == null) {
      defrange = data.optJSONObject("targetRange");
      range = data.optJSONObject("targetSelectionRange");
    }
   if (range == null) return;
   
   if (defrange != null) {
      int offset0 = lbf.mapRangeToStartOffset(defrange);
      int offset1 = lbf.mapRangeToEndOffset(defrange);
      JSONArray syms = lbf.getSymbols();
      for (int i = 0; i < syms.length(); ++i) {
         JSONObject sym = syms.getJSONObject(i);
         JSONObject rng = sym.getJSONObject("range");
         int doffset0 = lbf.mapRangeToStartOffset(rng);
         int doffset1 = lbf.mapRangeToEndOffset(rng);
         if (offset0 == doffset0 && offset1 == doffset1) {
            def = sym;
            break;
          }
         if (doffset0 <= offset0 && doffset1 >= offset1) {
            cont = sym;
          }
       }
    }
   if (what.equals("DEFS") || what.equals("DECL")) {
      if (def == null) {
         int off0 = lbf.mapRangeToStartOffset(range);
         int off1 = lbf.mapRangeToEndOffset(range);
         def = new JSONObject();
         def.put("name",lbf.getSegment(off0,off1-off0).toString());
         def.put("kind",27);            // local variable?
         def.put("location",
               createJson("uri",lbf.getUri(),"range",defrange));
         if (cont != null) {
            def.put("containerName",cont.getString("name"));
          }
       }
    }
   
   boolean skip = false;
   if (what.equals("HIGH")) {
      int kind = data.optInt("kind",0);
      if (kind != 0) {
         // if kind is specified
         if (write_only && kind != 3) skip = true;
         if (read_only && kind == 3) skip = true;
       }
    }
   if (what.equals("IMPL")) {
      System.err.println("CHECK HERE");
    }
   
   RefResult rr;
   switch (what) {
      case "REFS" :
         if (find_refs) rr = addResult(lbf,range);
         break;
      case "HIGH" :
         if (skip) removeResult(lbf,range);
         break;
      case "DECL" :
      case "DEFS" :
         if (find_defs) rr = addResult(lbf,range);
         else removeResult(lbf,range);
         for (RefResult rr1 : result_locs.keySet()) {
            rr1.setDefinition(def);
          }
         break;
      case "TYPE" :
         if (find_type) {
            rr = addResult(lbf,range);
            rr.setDefinition(def);
          }
         break;
      case "IMPL" :
         if (find_impls) {
            rr = addResult(lbf,range);
            rr.setDefinition(def);
          }
         break;
    }
}



private RefResult addResult(LspBaseFile lbf,JSONObject range)
{
   RefResult rr = new RefResult(lbf,range);
   RefResult rr1 = result_locs.putIfAbsent(rr,rr);
   if (rr1 != null) return rr1;
   return rr;
}


private void removeResult(LspBaseFile lbf,JSONObject range)
{
   RefResult rr = new RefResult(lbf,range);
   result_locs.remove(rr);
}



/********************************************************************************/
/*                                                                              */
/*      Search result                                                           */
/*                                                                              */
/********************************************************************************/

private class RefResult implements FindResult {
   
   private JSONObject match_range;
   private LspBaseFile match_file;
   private JSONObject def_result;
   private int start_offset;
   private int end_offset;
 
   RefResult(LspBaseFile file,JSONObject range) {
      match_range = range;
      match_file = file;
      start_offset = file.mapRangeToStartOffset(range);
      end_offset = file.mapRangeToEndOffset(range);
      def_result = null;
    }
   
   @Override public JSONObject getRange()               { return match_range; }
   @Override public LspBaseFile getFile()               { return match_file; }
   @Override public JSONObject getDefinition()          { return def_result; }
   
   void setDefinition(JSONObject def) {
      if (def != null && def_result == null) def_result = def;
    }
   
   @Override public int hashCode() {
      int hc = match_file.hashCode() + start_offset + end_offset;
      return hc;
    }
   
   @Override public boolean equals(Object o) {
      if (o instanceof RefResult) {
         RefResult rr = (RefResult) o;
         if (start_offset == rr.start_offset && end_offset == rr.end_offset &&
               match_file == rr.match_file) return true;
       }
      return false;
    }
   
   
}       // end of RefResult


}       // end of class LspBaseFindResult




/* end of LspBaseFindResult.java */

