/********************************************************************************/
/*                                                                              */
/*              LspBaseDebugTarget.java                                         */
/*                                                                              */
/*      Interface to a running process to debug                                 */
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



class LspBaseDebugTarget implements LspBaseConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

LspBaseDebugTarget(LspBaseDebugManager mgr,LspBaseLaunchConfig config)
{ }


/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

LspBaseDebugThread findThreadById(String id)
{
   return null;
}

String getId()                                  { return null; }



/********************************************************************************/
/*                                                                              */
/*      Action methods                                                          */
/*                                                                              */
/********************************************************************************/

void evaluateExpression(String bid,String eid,String expr,int frame,boolean stop)
{ }

void addBreakpointInRuntime(LspBaseBreakpoint bpt)      { }
void breakpointRemoved(LspBaseBreakpoint bpt)           { }
void startDebug()                                       { }

boolean debugAction(LspBaseDebugAction action)          { return false; }


}       // end of class LspBaseDebugTarget




/* end of LspBaseDebugTarget.java */

