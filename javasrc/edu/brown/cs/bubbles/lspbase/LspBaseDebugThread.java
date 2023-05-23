/********************************************************************************/
/*                                                                              */
/*              LspBaseDebugThread.java                                         */
/*                                                                              */
/*      Information for a debugger thread```                                       */
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

import java.util.ArrayList;
import java.util.List;

class LspBaseDebugThread implements LspBaseConstants
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


/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

List<LspBaseDebugStackFrame> getStackFrames()
{
   return new ArrayList<>();
}
     

String getName()                                { return null; }
String getLocalId()                             { return null; }





}       // end of class LspBaseDebugThread




/* end of LspBaseDebugThread.java */

