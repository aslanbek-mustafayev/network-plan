/**
 * Reads, analyses and displays project network plans.
 *
 * <p>Parse a fresh plan with {@link de.networkplan.NetworkPlanReader}, then
 * check {@link de.networkplan.ast.NetworkPlan#semanticErrors()} before asking
 * for CPM values. Reparse after input changes: cached attributes are intended
 * for a model that is not modified during analysis.
 *
 * <p>The generated {@code de.networkplan.ast} package includes the domain
 * model and the attributes declared in the JastAdd source files. Parser and
 * lexer packages are also generated; edit their specifications, not the output.
 */
package de.networkplan;
