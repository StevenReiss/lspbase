/********************************************************************************/
/*                                                                              */
/*              LspBaseLineOffsets.java                                         */
/*                                                                              */
/*      Manage line number <-> offset for a file                                */
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

import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;


class LspBaseLineOffsets implements LspBaseConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private int [] ide_offset;
private int max_ide;
private int max_char;
private int newline_adjust;



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

LspBaseLineOffsets(String newline,Reader input)
{
   newline_adjust = newline.length() - 1;
   ide_offset = new int[128];
   ide_offset[0] = -1;
   max_ide = 1;
   max_char = 0;
   
   setupIde(input,newline);
}



/********************************************************************************/
/*										*/
/*	Setup methods								*/
/*										*/
/********************************************************************************/

private void setupIde(Reader r,String nl)
{
   addIde(0);
   
   boolean lastcr = false;
   try {
      int i = 0;
      for ( ; ; ++i) {
	 int ch = r.read();
	 if (ch < 0) break;
	 if (nl.equals("\r")) {
	    if (ch == '\r') addIde(i+1);
	  }
	 else {
	    if (ch == '\n') addIde(i+1);
	    else if (lastcr)
	       addIde(i);
	    lastcr = (ch == '\r');
	  }
       }
      addIde(i);
      max_char = i;
      r.close();
    }
   catch (IOException e) {
      LspLog.logE("Problem reading input file: " + e);
    }
}



private void addIde(int i)
{
   grow(max_ide+1);
   ide_offset[max_ide++] = i;
}




/********************************************************************************/
/*										*/
/*	Update methods after edits						*/
/*										*/
/********************************************************************************/

synchronized void update(int soff,int eoff,String cnts)
{
   if (cnts == null && soff == eoff) return;
   
   int ct = 0;
   if (cnts != null) {
      for (int idx = cnts.indexOf('\n'); idx >= 0; idx = cnts.indexOf('\n',idx+1)) ++ct;
    }
   
   int idx0 = findIndex(soff);
   int idx1 = findIndex(eoff);
   int oct = idx1-idx0;
   int delta = 0;
   if (cnts != null) delta = cnts.length() - (eoff-soff);
   else delta = soff - eoff;
   
   LspLog.logD("UPDATE LINE OFFSETS " + soff + " " + eoff + " " + ct + " " + delta + " " + oct);
   
   grow(max_ide + ct - oct);	     // ensure we fit
   if (ct > oct) {
      for (int i = max_ide-1; i > idx1; --i) {
	 ide_offset[i+ct-oct] = ide_offset[i] + delta;
       }
    }
   else if (ct < oct || delta != 0) {
      for (int i = idx1+1; i < max_ide; ++i) {
	 ide_offset[i+ct-oct] = ide_offset[i] + delta;
       }
    }
   
   max_ide += ct-oct;
   
   int idx2 = idx0+1;
   if (cnts != null) {
      int lct = newline_adjust;
      for (int idx = cnts.indexOf('\n'); idx >= 0; idx = cnts.indexOf('\n',idx+1)) {
	 ide_offset[idx2] = soff + idx + 1 + lct;
	 ++idx2;
       }
    }
}


/********************************************************************************/
/*										*/
/*	Methods to find lines and line offsets for Java 			*/
/*										*/
/********************************************************************************/

synchronized int findOffset(int line)
{
   if (line < 0) return 0;
   if (line >= max_ide) return max_char;
   return ide_offset[line];
}



synchronized int findLine(int off)
{
   int sidx = Arrays.binarySearch(ide_offset,0,max_ide,off);
   if (sidx < 0) {
      sidx = -sidx - 2;
    }
   if (sidx < 0) {
      if (off < 0) return 0;
      if (off > ide_offset[max_ide-1]) return max_ide;
    }
   return sidx;
}



/********************************************************************************/
/*										*/
/*	Helper methods for maintaining the offset arrays			*/
/*										*/
/********************************************************************************/

private int findIndex(int off)
{
   int sidx = Arrays.binarySearch(ide_offset,0,max_ide,off);
   if (sidx > 0) return sidx;
   sidx = -sidx - 2;
   if (sidx < 0) sidx = 0;
   return sidx;
}




private void grow(int max)
{
   int sz = ide_offset.length;
   if (sz > max) return;
   while (sz < max) sz *= 2;
   ide_offset = Arrays.copyOf(ide_offset,sz);
}



}       // end of class LspBaseLineOffsets




/* end of LspBaseLineOffsets.java */

