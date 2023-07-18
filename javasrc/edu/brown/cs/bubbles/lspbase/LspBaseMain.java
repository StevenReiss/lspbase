/********************************************************************************/
/*										*/
/*		LspBaseMain.java						*/
/*										*/
/*	Main program for LSP-based back end for code bubbles			*/
/*										*/
/********************************************************************************/
/*	Copyright 2011 Brown University -- Steven P. Reiss		      */
/*********************************************************************************
 *  Copyright 2011, Brown University, Providence, RI.				 *
 *										 *
 *			  All Rights Reserved					 *
 *										 *
 * This program and the accompanying materials are made available under the	 *
 * terms of the Eclipse Public License v1.0 which accompanies this distribution, *
 * and is available at								 *
 *	http://www.eclipse.org/legal/epl-v10.html				 *
 *										 *
 ********************************************************************************/



package edu.brown.cs.bubbles.lspbase;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import edu.brown.cs.ivy.exec.IvySetup;
import edu.brown.cs.ivy.file.IvyFile;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

public class LspBaseMain implements LspBaseConstants
{



/********************************************************************************/
/*										*/
/*	Main program								*/
/*										*/
/********************************************************************************/

public static void main(String [] args)
{
   LspBaseMain lbm = new LspBaseMain(args);

   lbm.start();

   lbm.waitForShutDown();
}




/********************************************************************************/
/*										*/
/*	Private Storage 							*/
/*										*/
/********************************************************************************/

private File			root_directory;
private String			mint_handle;
private LspBaseMonitor		lsp_monitor;
private LspBaseProjectManager	project_manager;
private LspBaseDebugManager	debug_manager;

// private LspBaseEditor		lspbase_editor;
// private LspBaseFileManager	file_manager;
// private LspBaseSearch		lspbase_search;
// private LspBaseDebugManager	lspbase_debug;
// private IParser		lspbase_parser;
private File			work_directory;
private LspBaseThreadPool	thread_pool;
private Timer			lsp_timer;
// private LspBasePreferences	system_prefs;
private Map<String,LspBaseProtocol> active_protocols;
private Map<File,LspBaseProtocol> workspace_protocols;


static private LspBaseMain		lspbase_main;
private static final Map<String,LspBaseLanguageData> exec_map;


static {
   exec_map = new HashMap<>();
   String dartcmd = "dart language-server";
   dartcmd += " --client-id=$(ID)";
   dartcmd += " --client-version=1.2";
   dartcmd += " --protocol=lsp";
   String dapcmd = "flutter debug_adapter";
   exec_map.put("dart",new LspBaseLanguageData("dart",dartcmd,
	 dapcmd,".dart",false));
}






/********************************************************************************/
/*										*/
/*	Constructors								*/
/*										*/
/********************************************************************************/

public static LspBaseMain getLspMain()		{ return lspbase_main; }



private LspBaseMain(String [] args)
{
   lspbase_main = this;

   String rd = System.getProperty("edu.brown.cs.bubbles.lspbase.ROOT");
   if (rd == null) rd = "/pro/bubbles";
   root_directory = new File(rd);

   String hm = System.getProperty("user.home");
   File f1 = new File(hm);
   File f2 = new File(f1,".bubbles");
   File f3 = new File(f2,".ivy");
   if (!f3.exists() || !IvySetup.setup(f3)) IvySetup.setup();
   
   File f5 = new File(hm,"Lsp");
   work_directory = new File(f5,"workspace");
   
   mint_handle = System.getProperty("edu.brown.cs.bubbles.MINT");
   if (mint_handle == null) mint_handle = System.getProperty("edu.brown.cs.bubbles.mint");
   if (mint_handle == null) mint_handle = LSPBASE_MINT_NAME;
   
   thread_pool = new LspBaseThreadPool();
   lsp_timer = new Timer("LspBaseTimer",true);

   scanArgs(args);

   if (!work_directory.exists()) {
      work_directory.mkdirs();
    }

   active_protocols = new HashMap<>();
   workspace_protocols = new HashMap<>();

   LspLog.logI("STARTING");
}


/********************************************************************************/
/*										*/
/*	Argument Scanning Methods						*/
/*										*/
/********************************************************************************/

private void scanArgs(String [] args)
{
   boolean havelog = false;
   for (int i = 0; i < args.length; ++i) {
      if (args[i].startsWith("-")) {
	 if (args[i].startsWith("-m") && i+1 < args.length) {           // -m <mint handle>
	    mint_handle = args[++i];
	  }
	 else if (args[i].startsWith("-ws") && i+1 < args.length) {     // -ws <workspace>
	    work_directory = new File(args[++i]);
	    work_directory = IvyFile.getCanonical(work_directory);
	  }
	 else if (args[i].startsWith("-log") && i+1 < args.length) {     // -log <logfile>
	    LspLog.setLogFile(new File(args[++i]));
	    LspLog.setUseStdErr(false);
	    havelog = true;
	  }
	 else if (args[i].startsWith("-lang") && i+1 < args.length) {
	    LspLog.logD("SET LANGUAGE TO " + args[++i]);
	  }
	 else if (args[i].startsWith("-err")) {                         // -err
	    LspLog.setUseStdErr(true);
	  }
	 else badArgs();
       }
      else badArgs();
    }

   if (!havelog) {
      File f = new File(work_directory,"lspbase_log.log");
      LspLog.setLogFile(f);
    }
}



private void badArgs()
{
   System.err.println("LSPBASE: LspBaseMain [-m <mint>]");
   System.exit(1);
}



/********************************************************************************/
/*										*/
/*	Access methods								*/
/*										*/
/********************************************************************************/


File getWorkSpaceDirectory()				{ return work_directory; }

File getRootDirectory() 			{ return root_directory; }

LspBaseProjectManager getProjectManager()		{ return project_manager; }
LspBaseDebugManager getDebugManager()			{ return debug_manager; }


LspBaseFile getFileData(String proj,String file)
{
   return getFileData(proj,new File(file));
}

LspBaseFile getFileData(String proj,File file)
{
   try {
      LspBaseProject lbp = project_manager.findProject(proj);
      return lbp.findFile(file);
    }
   catch (LspBaseException e) {
      return null;
    }
}



/********************************************************************************/
/*										*/
/*	Processing methods							*/
/*										*/
/********************************************************************************/

private void start()
{
   try {
      project_manager = new LspBaseProjectManager(this);
      debug_manager = new LspBaseDebugManager(this);
      lsp_monitor = new LspBaseMonitor(this,mint_handle);
      debug_manager.start();
    }
   catch (LspBaseException e) {
      LspLog.logE("Problem initializing: " + e,e);
      System.exit(1);
    }
}


private void waitForShutDown()
{
   lsp_monitor.waitForShutDown();

   LspLog.logD("Start shutdown");

   for (LspBaseProtocol proto : workspace_protocols.values()) {
      proto.shutDown();
    }

   LspLog.logD("Exiting");

   System.exit(0);
}



/********************************************************************************/
/*										*/
/*	Protocol methods							*/
/*										*/
/********************************************************************************/

LspBaseProtocol findProtocol(File ws,String lang,List<LspBasePathSpec> paths)
{
   LspBaseProtocol proto = null;

   synchronized (workspace_protocols) {
      proto = workspace_protocols.get(ws);
      if (proto != null) return proto;

      LspBaseLanguageData ld = exec_map.get(lang);
      if (ld == null) return null;
      if (!ld.isSingleWorkspace()) {
	 proto = active_protocols.get(lang);
	 if (proto != null) {
	    proto.addWorkspace(ws,paths);
	    return proto;
	  }
       }
      proto = new LspBaseProtocol(ws,paths,ld);
      if (ld.isSingleWorkspace()) active_protocols.put(lang,proto);
      workspace_protocols.put(ws,proto);
      if (ld.isSingleWorkspace()) {
         try {
            proto.initialize();
          }
         catch (LspBaseException e) {
            LspLog.logE("Can't initialize protocol",e);
            proto = null;
          }
       }
    }

   return proto;
}


LspBaseLanguageData getLanguageData(String lang)
{
   return exec_map.get(lang);
}



/********************************************************************************/
/*										*/
/*	Message sending 							*/
/*										*/
/********************************************************************************/

public IvyXmlWriter beginMessage(String typ)
{
   return lsp_monitor.beginMessage(typ);
}


public IvyXmlWriter beginMessage(String typ,String bid)
{
   return lsp_monitor.beginMessage(typ,bid);
}


void finishMessage(IvyXmlWriter xw)
{
   lsp_monitor.finishMessage(xw);
}


public String finishMessageWait(IvyXmlWriter xw)
{
   return lsp_monitor.finishMessageWait(xw);
}


public String finishMessageWait(IvyXmlWriter xw,long delay)
{
   return lsp_monitor.finishMessageWait(xw,delay);
}



/********************************************************************************/
/*										*/
/*	Thread pool for background tasks					*/
/*										*/
/********************************************************************************/

public void startTask(Runnable r)
{
   if (r != null) thread_pool.execute(r);
}


public void startTaskDelayed(Runnable r,long delay)
{
   if (r == null) return;

   LspBaseDelayExecute pde = new LspBaseDelayExecute(r);
   lsp_timer.schedule(pde,delay);
}


public void finishTask(Runnable r)
{
   if (r != null) thread_pool.remove(r);
}


public void scheduleTask(TimerTask r,long delay,long inter)
{
   lsp_timer.schedule(r,delay,inter);
}



private static class LspBaseThreadPool extends ThreadPoolExecutor implements ThreadFactory {

   private static int thread_counter = 0;

   LspBaseThreadPool() {
      super(LSPBASE_CORE_POOL_SIZE,LSPBASE_MAX_POOL_SIZE,
	    LSPBASE_POOL_KEEP_ALIVE_TIME,TimeUnit.MILLISECONDS,
	    new LinkedBlockingQueue<Runnable>());

      setThreadFactory(this);
    }

   @Override public Thread newThread(Runnable r) {
      Thread t = new Thread(r,"LspBaseWorkerThread_" + (++thread_counter));
      t.setDaemon(true);
      return t;
    }

   @Override protected void afterExecute(Runnable r,Throwable t) {
      super.afterExecute(r,t);
      if (t != null) {
	 LspLog.logE("Problem with background task " + r.getClass().getName() + " " + r,t);
       }
    }

}	// end of inner class LspBaseThreadPool


private class LspBaseDelayExecute extends TimerTask {

   private Runnable run_task;

   LspBaseDelayExecute(Runnable r) {
      run_task = r;
    }

   @Override public void run() {
      thread_pool.execute(run_task);
    }

}	// end of LspBaseDelayExecute




}	// end of class LspBaseMain




/* end of LspBaseMain.java */

