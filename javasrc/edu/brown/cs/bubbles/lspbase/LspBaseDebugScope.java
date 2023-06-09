/********************************************************************************/
/*                                                                              */
/*              LspBaseDebugScope.java                                          */
/*                                                                              */
/*      Representation of a scope for debugging                                 */
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

import org.json.JSONObject;

class LspBaseDebugScope implements LspBaseConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private LspBaseDebugThread for_thread;
private JSONObject scope_data;



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

LspBaseDebugScope(LspBaseDebugStackFrame frm,JSONObject obj) {
   scope_data = obj;
   for_thread = frm.getThread();
}



/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

LspBaseDebugThread getThread()                  { return for_thread; }

int getReferenceNumber()             
{
   return scope_data.getInt("variablesReference"); 
}

String getDelayName()
{
   String s = scope_data.getString("name");
   if (for_thread.getTarget().isLocalScope(s)) return null;
   String name = "< " + s + " >";
   return name;
}

boolean isLocal() 
{
   String s = scope_data.getString("name");
   return for_thread.getTarget().isLocalScope(s);
}

boolean isStatic()
{
   String s = scope_data.getString("name");
   return for_thread.getTarget().isStaticScope(s);
}


boolean isPrimitiveType(String typ)
{
   return for_thread.getTarget().isPrimitiveType(typ);
}


boolean isStringType(String typ)
{
   return for_thread.getTarget().isStringType(typ);
}


}       // end of class LspBaseDebugScope




/* end of LspBaseDebugScope.java */

