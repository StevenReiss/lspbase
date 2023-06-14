/********************************************************************************/
/*                                                                              */
/*              LspBaseIndenter.java                                            */
/*                                                                              */
/*      Simple default indenter for when formating fails                        */
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


class LspBaseIndenter implements LspBaseConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private LspBaseFile     for_file;
private int             start_offset;
private LineCol         split_pos;
private int             current_line;
private String          line_text;
private int             line_length;

private int             last_sig;
private int             nest_level;
private int             string_char;
private boolean         cmmt_flag;
private boolean         eos_first;
private int             nest_pos;
private boolean         fct_flag;
private boolean         case_flag;
private boolean         label_flag;
private boolean         rbr_flag;
private int             cma_line;
private boolean         lbr_flag;
private int             last_semi;
private int             init_indent;


private static int L4 = 2;              // default indent
private static int L5 = 2;              // indent w/ no last sig
private static int L6 = 0;              // indent for }
private static boolean L7 = true;       // unnest top level functions



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

LspBaseIndenter(LspBaseFile file,int pos)
{
   for_file = file;
   start_offset = pos;
   current_line = file.mapOffsetToLine(pos);
   line_text = null;
   line_length = 0;
   
   last_sig = -1;
   nest_level = 0;
   string_char = 0;
   cmmt_flag = false;
   eos_first = false;
   nest_pos = -1;
   fct_flag = false;
   case_flag = false;
   label_flag = false;
   rbr_flag = false;
   cma_line = -1;
   lbr_flag = false;
   last_semi = -1;
   init_indent = -1;
}



/********************************************************************************/
/*                                                                              */
/*      Compute the indentation                                                 */
/*                                                                              */
/********************************************************************************/

int computeIndent()
{
   split_pos = null;
   return computeActualIndent();
}


int computePartialIndent()
{
   split_pos = for_file.mapOffsetToLineColumn(start_offset);
   current_line = current_line+1;
   return computeActualIndent();
}



private int computeActualIndent()
{
   getLine();
   int x = 0;
   
   // get initial indent
   for ( ; getChar(x) == ' '; ++x);
   char c = getChar(x);
   if (x < line_length) init_indent = x;
   if (c == '#') return 0;
   if (c == '}') {
      nest_level = 1;
      eos_first = true;
      rbr_flag = true;
    }
   else if (c == '{') lbr_flag = true;
   else if (matchInLine(x,"case ")) case_flag = true;
   else if (matchInLine(x,"default ")) case_flag = true; 
   else if (matchInLine(x,"default:")) case_flag = true; 
   else if (isLabel(x)) label_flag = true;
   
   x = 0;
   loop: for ( ; ; ) {
      while (x <= 0) {
         int lstrfg = 0;
         --current_line;
         fct_flag = false;
         if (current_line < 0) break loop;
         getLine();
         if (getChar(0) == '#') x = 0;
         else {
            for (x = 0; x < line_length; ++x) {
               char chx = getChar(x);
               if (chx == '"' || chx == '\'') {
                  if (x == 0 || getChar(x-1) == '\\') continue;
                  if (lstrfg == 0) lstrfg = chx;
                  else if (lstrfg == chx) lstrfg = 0;
                }
               else if (lstrfg == 0 && chx == '/' && getChar(x+1) == '/') break;
             }
          }
       }
      --x;
      c = getChar(x);
      
      if (string_char != 0) {
         if (c == string_char && (x == 0 || getChar(x-1) == '\\')) {
            string_char = 0;
            c = 'X';
          }
         else continue;
       }
      if (cmmt_flag) {
         if (c == '/' && getChar(x+1) == '*') cmmt_flag = false;
         continue;
       }
      if (c == '/' && getChar(x-1) == '*') {
         cmmt_flag = true;
         --x;
         continue;
       }
      
      switch (c) {
         case ' ' :
            break;
         case '"' :
         case '\'' :
            string_char = c;
            break;
            
         case '(' :
            if (last_semi != current_line && getChar(x+1) != ')' &&
                  !isPrototype(x+1)) {
               fct_flag = true;
             }
	    //$FALL-THROUGH$
	 case '{' :
            if (last_sig < 0 && last_semi < 0) case_flag = false;
            if (nest_level > 0) {
               --nest_level;
               if (nest_level == 0) last_sig = x;
             }
            else if (last_sig < 0 && x > 0) nest_pos = x;
            else if (x == 0 && last_sig < 0) {
               nest_pos = 0;
               break loop;
             }
            cma_line = -1;
            break;
            
         case '}' :
            if (nest_level == 0) {
               if (x == 0) {
                  last_sig = -1;
                  nest_pos = -1;
                  break loop;
                }
               if (last_sig >= 0) break loop;
               eos_first = true;
             }
            ++nest_level;
            break;
            
         case ')' :
            ++nest_level;
            break;
            
         case ':' :
            if (nest_level > 0) break;
            int i = 0;
            while (getChar(i) == ' ') ++i;
            if (matchInLine(i,"case ") ||
                  matchInLine(i,"default ") ||
                  matchInLine(i,"default:")) {
               while (Character.isJavaIdentifierPart(getChar(i))) ++i;
               while (getChar(i) == ' ') ++i;
               if (i == x) x = 0;
             }
            else {
               if (nest_pos < 0 && last_sig < 0) {
                  last_sig = i;
                  nest_pos = i;
                }
               break loop;
             }
            break;
            
         case ';' :
            last_semi = current_line;
            if (nest_level > 0) break;
            if (last_sig >= 0) break loop;
            eos_first = true;
            break;
            
         case ',' :
            if (nest_level == 0) cma_line = current_line;
            break;
            
         default :
            if (nest_level > 0) break;
            last_sig = x;
            if (x == 0) {
               if (fct_flag && L7) nest_pos = 0;
               last_sig = -1;
               if (getChar(0) == '/' && getChar(1) == '*' && nest_pos < 0) {
                  last_sig = init_indent;
                  eos_first = true;
                }
               break loop;
             }
            break;
       }
    }
   
   int rslt = last_sig;
   if (last_sig < 0 && nest_pos < 0) rslt = 0;
   else if (last_sig < 0) rslt = L5;
   else if (nest_pos >= 0) rslt = last_sig + L5;
   else if (!eos_first && (cma_line < 0 || cma_line == current_line)) {
      rslt = last_sig + L4;
    }
   
   if (lbr_flag && rslt == L5) rslt = 0;
   if (rbr_flag && rslt != 0) rslt += L6;
   else if (case_flag) rslt -= L5;
   else if (label_flag) rslt -= L5;
 
   if (rslt < 0) rslt = 0;

   return rslt;
}




/********************************************************************************/
/*                                                                              */
/*      Utility methods                                                         */
/*                                                                              */
/********************************************************************************/

String getLine()
{
   int lno = current_line;
   int soffset = for_file.mapLineToOffset(current_line);
   int eoffset = for_file.mapLineToOffset(current_line+1)-1;
   
   if (split_pos != null) {
      if (lno > split_pos.getLine()) {
         lno -= 1;
         if (lno == split_pos.getLine()) {
            soffset = start_offset;
          }
         else {
            soffset = for_file.mapLineToOffset(lno);
            eoffset = for_file.mapLineToOffset(lno+1)-1;
          }
       }
      else if (lno == split_pos.getLine()) {
         eoffset = start_offset;
       }
   
    }
   
   String txt = for_file.getText(soffset,eoffset-soffset);
   if (txt == null) {
      LspLog.logD("NULL TEXT " + soffset + " " + eoffset);
      txt = "";
    }
   
   if (txt.contains("\t")) {
      int tabsize = for_file.getTabSize();
      int length = 0;
      StringBuffer buf = new StringBuffer();
      for (int i = 0; i < txt.length(); ++i) {
         char ch = txt.charAt(i);
         if (ch == '\t') {
            int rm = length%tabsize;
            int ct = tabsize - rm;
            for (int j = 0; j < ct; ++j) buf.append(" ");
          }
         else {
            buf.append(ch);
          }
       }
      txt = buf.toString(); 
    }
   txt = txt.replace("::","XX");

   line_text = txt;
   line_length = txt.length();
   
   return txt;
}


char getChar(int pos)
{
   if (pos >= line_length || pos < 0) return 0;
   return line_text.charAt(pos);
}


boolean matchInLine(int pos,String txt)
{
   int ln = txt.length();
   for (int i = 0; i < ln; ++i) {
      if (txt.charAt(i) != line_text.charAt(pos+i)) return false;
    }
   
   return true;
}


boolean isLabel(int pos)
{
   for (int i = pos; i < line_length; ++i) {
      char ch = line_text.charAt(i);
      if (Character.isJavaIdentifierPart(ch)) ;
      else if (ch == ':') {
         if (i+1 < line_length && line_text.charAt(i+1) == ':') break;
         return true;
       }
      else break;
    }
   return false;
}


boolean isPrototype(int pos) 
{
   boolean haveid = false;
   boolean inid = false;
   
   for (int i = pos; i < line_length; ++i) {
      char c = getChar(i);
      if (c == 0 || c <= ')' || c == ',') break;
      if (Character.isJavaIdentifierPart(c) || c == '$') {
         if (!inid && haveid) return true;
         inid = true;
         haveid = true;
       }
      else inid = false;
    }
   
   return false;
}

}       // end of class LspBaseIndenter




/* end of LspBaseIndenter.java */

