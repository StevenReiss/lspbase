/********************************************************************************/
/*                                                                              */
/*              LspLog.java                                                     */
/*                                                                              */
/*      Logging for LspBase package                                             */
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
import java.io.FileOutputStream;
import java.io.PrintStream;

class LspLog implements LspBaseConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private static PrintStream log_file = null;
private static LspBaseLogLevel log_level = LspBaseLogLevel.DEBUG;
private static boolean use_stderr = false;


/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

static void setLogLevel(LspBaseLogLevel lvl)
{
   log_level = lvl;
}


static void setLogFile(File f)
{
   try {
      log_file = new PrintStream(new FileOutputStream(f),true);
    }
   catch (java.io.FileNotFoundException e) {
      log_file = null;
      LspLog.logE("Error initializing file: " + e);
    }
}


static void setUseStdErr(boolean fg)
{
   use_stderr = fg;
}



/********************************************************************************/
/*										*/
/*	Logging methods 							*/
/*										*/
/********************************************************************************/

static public void logE(String msg,Throwable t) 
{ 
   log(LspBaseLogLevel.ERROR,msg,t); 
}

static public void logE(String msg)		
{ 
   log(LspBaseLogLevel.ERROR,msg,null); 
}

static public void logW(String msg)	
{ 
   log(LspBaseLogLevel.WARNING,msg,null); 
}

static public void logI(String msg)		
{ 
   log(LspBaseLogLevel.INFO,msg,null);
}

static public  void logD(String msg)	
{
   log(LspBaseLogLevel.DEBUG,msg,null);
}

static public  void logDX(String msg)
{
   log(LspBaseLogLevel.DEBUG,msg,new Throwable(msg));
}


static public void logX(String msg)
{
   Throwable t = new Throwable(msg);
   logE(msg,t);
}



static public void log(String msg)		{ logI(msg); }



static public void log(LspBaseLogLevel lvl,String msg,Throwable t)
{
   if (lvl.ordinal() > log_level.ordinal()) return;
   
   String s = lvl.toString().substring(0,1);
   String pfx = "LSPBASE:" + s + ": ";
   
   if (log_file != null) {
      log_file.println(pfx + msg);
      dumpTrace(null,t);
    }
   if (use_stderr || log_file == null) {
      System.err.println(pfx + msg);
      dumpTrace(null,t);
      System.err.flush();
    }
   
   if (log_file != null) log_file.flush();
}



static private void dumpTrace(String pfx,Throwable t) {
   if (t == null) return;
   if (log_file != null) {
      if (pfx != null) log_file.print(pfx);
      t.printStackTrace(log_file);
    }
   if (log_file == null || use_stderr) {
      if (pfx != null) System.err.print(pfx);
      t.printStackTrace();
    }
   dumpTrace("CAUSED BY: ",t.getCause());
}


}       // end of class LspLog




/* end of LspLog.java */

