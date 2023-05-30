/********************************************************************************/
/*                                                                              */
/*              LspBasePatternResult.java                                       */
/*                                                                              */
/*      Handle results from PATTERNSEARCH                                       */
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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

class LspBasePatternResult implements LspBaseConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private LspBaseProject  for_project;
private Set<Integer>    valid_types;
private boolean         allow_system;

private Pattern         name_pattern;
private String          match_pattern;
private Pattern         container_pattern;
private Pattern         file_pattern;
private Pattern         detail_pattern;

private List<JSONObject> result_syms;




/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

LspBasePatternResult(LspBaseProject proj,String pat,String typ,boolean system)
{
   for_project = proj;
   result_syms = new ArrayList<>();
   valid_types = decodeTypes(typ);
   name_pattern = null;
   match_pattern = null;
   container_pattern = null;
   file_pattern = null;
   detail_pattern = null;
   
   decodePattern(pat,typ);
}



/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

String getMatchPattern()                        { return match_pattern; }

List<JSONObject> getResults()                   { return result_syms; }



/********************************************************************************/
/*                                                                              */
/*      Type decoding                                                           */
/*                                                                              */
/********************************************************************************/

Set<Integer> decodeTypes(String typ)
{
   Set<Integer> rslt = null;
   
   switch (typ) {
      case "CONSTRUCTOR" :
         rslt = Set.of(9);
         break;
      case "METHOD" :
         rslt = Set.of(6,12,21);
         break;
      case "FIELD" :
         rslt = Set.of(8,13,14,20,22);
         break;
      case "TYPE" :
         rslt = Set.of(5,7,10,11,23);
         break;
      case "PACKAGE" :
         rslt = Set.of(2,3,4);
         break;
      case "MODULE" :
         rslt = Set.of(2);
         break;
      case "CLASS" :
         rslt = Set.of(5);
         break;
      case "INTERFACE" :
         rslt = Set.of(11);
         break;
      case "ENUM" :
         rslt = Set.of(10);
         break;
      case "CLASS_AND_ENUM" :
         rslt = Set.of(5,10);
         break;
      case "CLASS_AND_INTERFACE" :
         rslt = Set.of(5,11);
         break;
      case "ANNOTATION" :
         rslt = Set.of(7);
         break;
      default :
         LspLog.logE("Unknown search type " + typ);
         break;
    }
   
   return rslt;
}



/********************************************************************************/
/*                                                                              */
/*      Pattern docoding                                                        */
/*                                                                              */
/********************************************************************************/

private void decodePattern(String pat,String typ)
{
   if (pat.startsWith("null.")) pat = pat.substring(5);
   
   switch (typ) {
      case "CONSTRUCTOR" :
         decodeConstructorPattern(pat);
         break;
      default :
      case "METHOD" :
         decodeMethodPattern(pat);
         break;
      case "FIELD" :
         decodeFieldPattern(pat);
         break;
      case "PACKAGE" :
      case "MODULE" :
         decodePackagePattern(pat);
         break;
      case "TYPE" :
      case "CLASS" :
      case "INTERFACE" :
      case "ENUM" :
      case "CLASS_AND_ENUM" :
      case "CLASS_AND_INTERFACE" :
      case "ANNOTATION" :
         decodeTypePattern(pat);
         break;
    }
}



private void decodeTypePattern(String pat)
{
   // TYPE PATTERNS: [qualification '.']typeName ['<' typeArguments '>']
   //		     [moduleName1[,moduleName2,..]]/[qualification '.']typeName ['<' typeArguments '>']
   
   int idx1 = pat.indexOf("/");
   if (idx1 > 0) pat = pat.substring(idx1+1);           // ignore module names 
   int idx2 = pat.indexOf("<");
   if (idx2 > 0) { 
      pat = pat.substring(0,idx2);      // ignore type arguments for now
    }
   int idx3 = pat.lastIndexOf(".");
   if (idx3 > 0) {
      String qual = pat.substring(0,idx3);
      container_pattern = makeRegexFromWildcard(qual);
      pat = pat.substring(idx3+1);
    }
   name_pattern = makeRegexFromWildcard(pat);
   match_pattern = makeLspFromWildcard(pat);
}



private void decodeMethodPattern(String pat)
{ 
   // METHOD PATTERNS:
   //	   [declaringType '.'] ['<' typeArguments '>'] methodName ['(' parameterTypes ')'] [returnType]
   
   int idx0 = pat.indexOf("(");
   int idx1 = pat.indexOf("<");
   if (idx1 >= idx0) idx1 = -1;
   if (idx1 > 0) {
      int idx2 = -1;
      if (idx0 > 0) {
         idx2 = pat.lastIndexOf(">",idx0);
       }
      else {
         idx2 = pat.lastIndexOf(">");
       }
      pat = pat.substring(0,idx1) + pat.substring(idx2+2);
      idx0 = pat.indexOf("(");
    }
   int idx4 = -1;
   if (idx0 > 0) idx4 = pat.lastIndexOf(".",idx0);
   else idx4 = pat.lastIndexOf(".");
   if (idx4 > 0) {
      String qual = pat.substring(0,idx4);
      container_pattern = makeRegexFromWildcard(qual);
      pat = pat.substring(idx4+1);
    }
   if (idx0 > 0) {
      int idx5 = pat.lastIndexOf(")");
      String args = pat.substring(idx0,idx5+1);
      detail_pattern = makeRegexFromWildcard(args);
      pat = pat.substring(0,idx0);
    }
   name_pattern = makeRegexFromWildcard(pat);
   match_pattern = makeLspFromWildcard(pat);
}


private void decodeConstructorPattern(String pat)
{
   decodeMethodPattern(pat);
}


private void decodeFieldPattern(String pat)
{
   int idx0 = pat.lastIndexOf(".");
   if (idx0 > 0) {
      String qual = pat.substring(0,idx0);
      container_pattern = makeRegexFromWildcard(qual);
      pat = pat.substring(idx0+1);
    }
   name_pattern = makeRegexFromWildcard(pat);
   match_pattern = makeLspFromWildcard(pat);  
}



private void decodePackagePattern(String pat)
{
   int idx0 = pat.lastIndexOf(".");
   if (idx0 > 0) {
      String qual = pat.substring(0,idx0);
      container_pattern = makeRegexFromWildcard(qual);
      pat = pat.substring(idx0+1);
    }
   name_pattern = makeRegexFromWildcard(pat);
   match_pattern = makeLspFromWildcard(pat);  
}



/********************************************************************************/
/*                                                                              */
/*      File pattern to Regex                                                   */
/*                                                                              */
/********************************************************************************/

Pattern makeRegexFromWildcard(String wc)
{
   String q1 = wc.replace(".","\\.");
   q1 = q1.replace("*","(.*)");
   
   Pattern pat = Pattern.compile(q1);
   return pat;
}


String makeLspFromWildcard(String wc)
{
   String q1 = wc.replace("*","");
   return q1;
}


/********************************************************************************/
/*                                                                              */
/*      Add results found                                                       */
/*                                                                              */
/********************************************************************************/

void addResult(JSONArray syms,JSONObject err) 
{
   if (err != null) {
      LspLog.logD("Search Result Error " + err.toString(2));
    }
   else {
      for (int i = 0; i < syms.length(); ++i) {
         JSONObject sym = syms.getJSONObject(i);
         String nm = sym.getString("name");
         String detail = sym.optString("detail");
         int idx1 = nm.indexOf("(");
         if (idx1 > 0) {
            detail = nm.substring(idx1);
            nm = nm.substring(0,idx1);
            if (detail.equals("(\u2026)")) detail = null;
          }
         
         Matcher m1 = name_pattern.matcher(nm);
         if (!m1.matches()) continue;
         int kind = sym.getInt("kind");
         if (valid_types != null && !valid_types.contains(kind)) continue;
         if (container_pattern != null) {
            String container = sym.optString("containerName");
            if (container != null) {
               Matcher m2 = container_pattern.matcher(container);
               if (!m2.matches()) continue;
             }
          }
         if (detail_pattern != null && detail != null) {
            Matcher m3 = detail_pattern.matcher(detail);
            if (!m3.matches()) continue;
          }
         
         JSONObject loc = sym.getJSONObject("location");
         String uri = loc.getString("uri");
         if (!allow_system) {
            LspBaseFile lbf = for_project.findFile(uri);
            if (lbf == null) continue;
          }
         if (detail_pattern != null) {
            
          }
         LspLog.logD("Search Result " + sym.toString(2));
         result_syms.add(sym);
       }
    }
}

}       // end of class LspBasePatternResult




/* end of LspBasePatternResult.java */

