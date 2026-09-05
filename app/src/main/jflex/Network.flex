package de.networkplan.lexer;

import beaver.Scanner;
import beaver.Symbol;

import de.networkplan.parser.NetworkParser.Terminals;

%%

%public
%class NetworkLexer
%extends Scanner
%unicode
%line
%column
%function nextToken
%type Symbol
%yylexthrow Scanner.Exception
%xstate ACTIVITY_NAME

%eofval{
    return new Symbol(Terminals.EOF, "end-of-file");
%eofval}

%{
    private Symbol symbol(short id) {
        return new Symbol(
            id,
            yyline + 1,
            yycolumn + 1,
            yylength()
        );
    }

    private Symbol symbol(short id, Object value) {
        return new Symbol(
            id,
            yyline + 1,
            yycolumn + 1,
            yylength(),
            value
        );
    }
%}

LineTerminator = \r\n|\r|\n
WhiteSpace     = {LineTerminator} | [ \t\f]
Identifier     = [A-Za-z_][A-Za-z0-9_]*
ActivityName   = [A-Za-z0-9_]+(\.[A-Za-z0-9_]+)*
Integer        = 0|[1-9][0-9]*

%%

"project" {
    return symbol(Terminals.PROJECT);
}

"activity" {
    yybegin(ACTIVITY_NAME);
    return symbol(Terminals.ACTIVITY);
}

"duration" {
    return symbol(Terminals.DURATION);
}

"dependency" {
    yybegin(ACTIVITY_NAME);
    return symbol(Terminals.DEPENDENCY);
}

"{" {
    return symbol(Terminals.LBRACE);
}

"}" {
    return symbol(Terminals.RBRACE);
}

"->" {
    yybegin(ACTIVITY_NAME);
    return symbol(Terminals.ARROW);
}

";" {
    return symbol(Terminals.SEMICOLON);
}

{Integer} {
    return symbol(
        Terminals.INTEGER,
        Integer.valueOf(yytext())
    );
}

{Identifier} {
    return symbol(
        Terminals.ID,
        yytext()
    );
}

<ACTIVITY_NAME> {ActivityName} {
    yybegin(YYINITIAL);
    return symbol(Terminals.ID, yytext());
}

<YYINITIAL, ACTIVITY_NAME> {WhiteSpace}+ {
    /* Ignore whitespace. */
}

<YYINITIAL, ACTIVITY_NAME> [^] {
    throw new Scanner.Exception(
        yyline + 1,
        yycolumn + 1,
        "Unknown character '" + yytext() + "'"
    );
}
