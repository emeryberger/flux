package edu.umass.cs.flux;

import java_cup.runtime.Symbol;

%%
%cup
%eofval{
  return new Symbol(sym.EOF);
%eofval}
%line

%{
  public int getLine() {
    return yyline;
  }
%}
%%
"=>" { return new Symbol(sym.ARROW); }
"(" { return new Symbol(sym.LEFT_PAREN); }
")" { return new Symbol(sym.RIGHT_PAREN); }
"[" { return new Symbol(sym.LEFT_SQ_BRACE); }
"]" { return new Symbol(sym.RIGHT_SQ_BRACE); }
"{" { return new Symbol(sym.LEFT_CR_BRACE); }
"}" { return new Symbol(sym.RIGHT_CR_BRACE); }
"->" { return new Symbol(sym.PIPE); }
":" { return new Symbol(sym.COLON); }
";" { return new Symbol(sym.SEMI); }
"," { return new Symbol(sym.COMMA); }
"=" { return new Symbol(sym.EQUALS); }
"*" { return new Symbol(sym.STAR); }
"+" { return new Symbol(sym.PLUS); }
"?" { return new Symbol(sym.QUESTION); }
"!" { return new Symbol(sym.EXCLAMATION); }
"typedef" { return new Symbol(sym.TYPEDEF); }
"source" { return new Symbol(sym.SOURCEDEF); }
"handle" { return new Symbol(sym.HANDLE); }
"error" {return new Symbol(sym.ERROR); }
"atomic" {return new Symbol(sym.ATOMIC); }
"program" {return new Symbol(sym.PROGRAM); }
"session" {return new Symbol(sym.SESSION); }
[0-9]+ { return new Symbol(sym.NUMBER, new Integer(yytext())); }
[a-zA-Z_*][a-zA-Z0-9_*]* 
{ return new Symbol(sym.IDENTIFIER, new String(yytext()));}
[ \t\r\n\f] { /* ignore white space. */ }
. { System.err.println("Illegal character: "+yytext()); }
