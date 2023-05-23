LSPBASE

This package is a back-end for Code Bubbles that uses LSP and DAP (standard protocols with
multiple language implementations).

We are initially implementing it using the DART back ends.  If it works sufficiently, we
hope to integrate it directly into Code Bubbles (e.g. replace BUMP with this interface,
keeping data structures such as projects only internal to Code Bubbles).  If not, it
should at least provide an implementation of Code Bubbles for dart.

(Note that DAP servers are not as common as LSP servers, so they might have to be implemented
separately.)
