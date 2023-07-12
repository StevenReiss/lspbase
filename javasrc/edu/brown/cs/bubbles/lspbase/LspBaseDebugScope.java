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

private LspBaseDebugStackFrame for_frame;
private JSONObject scope_data;



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

LspBaseDebugScope(LspBaseDebugStackFrame frm,JSONObject obj) {
   scope_data = obj;
   for_frame = frm;
}

LspBaseDebugScope(LspBaseDebugVariable var)
{
   scope_data = createJson("variablesReference",var.getReference(),
         "name","Variable");
   for_frame = var.getFrame();
}




/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

int getReferenceNumber()             
{
   return scope_data.getInt("variablesReference"); 
}

LspBaseDebugStackFrame getFrame()
{
   return for_frame;
}


String getDelayName()
{
   String s = scope_data.getString("name");
   if (for_frame.isLocalScope(s)) return null;
   String name = "< " + s + " >";
   return name;
}

boolean isLocal() 
{
   String s = scope_data.getString("name");
   return for_frame.isLocalScope(s);
}

boolean isStatic()
{
   String s = scope_data.getString("name");
   return for_frame.isStaticScope(s);
}


boolean isPrimitiveType(String typ)
{
   return for_frame.isPrimitiveType(typ);
}


boolean isStringType(String typ)
{
   return for_frame.isStringType(typ);
}


}       // end of class LspBaseDebugScope




/* end of LspBaseDebugScope.java */

