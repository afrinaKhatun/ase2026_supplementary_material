package analyzer;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.*;

import java.util.ArrayList;
import java.util.List;

public class AssertionStatementRemover {
        public static MethodDeclaration withoutAssertions(MethodDeclaration original) {
            MethodDeclaration updated = original.clone();// don’t mutate caller’s node
            updated.setName("tempName");
            if (updated.getBody().isPresent()) {
                processBlock(updated.getBody().get());
            }
            return updated;
        }

        /** Same as above, but returns the updated method source as a String. */
        public static String withoutAssertionsAsString(MethodDeclaration original) {
            return withoutAssertions(original).toString();
        }

        // --- internals ---

        /** Walk each statement; if top-level is a method call and name is assert* or fail, remove the statement. */
        private static int processBlock(BlockStmt block) {
            int removed = 0;
            // 1) Remove Java keyword assertions:  assert <cond> ;   // no JUnit involved
            List<AssertStmt> asserts = new ArrayList<>(block.findAll(AssertStmt.class)); // avoid CME
            for (int i = 0; i < asserts.size(); i++) {
                asserts.get(i).remove();
                removed++;
            }
            List<Statement> copy = new ArrayList<>(block.getStatements()); // avoid CME
            for (Statement st : copy) {
                if (isTopLevelMethodCall(st)) {
                    MethodCallExpr mc = (MethodCallExpr) ((ExpressionStmt) st).getExpression();
                    if (isAssertOrFail(mc)) {
                        st.remove();        // remove entire line
                        removed++;
                        continue;
                    }
                }
                //removed += processLambdasInStatement(st);
                // recurse into nested statement bodies
                removed += recurse(st);
            }
            return removed;
        }

        private static boolean isTopLevelMethodCall(Statement st) {
            if (!(st instanceof ExpressionStmt)) return false;
            Expression e = ((ExpressionStmt) st).getExpression();
            return e instanceof MethodCallExpr;
        }

        /** Only the TOP-LEVEL call name matters. Argument contents are intentionally ignored. */
        private static boolean isAssertOrFail(MethodCallExpr call) {
            String n = call.getNameAsString().toLowerCase();
            return n.startsWith("assert") || n.equals("fail");
        }

    // Helper to handle a single non-block statement by wrapping it in a temp block.
    private static int processSingle(Statement s) {
        BlockStmt tmp = new BlockStmt();
        tmp.addStatement(s);
        return processBlock(tmp);
    }

    /** Recurse into nested statement blocks using simple loops; lambdas are ignored. */
    private static int recurse(Statement st) {
        int removed = 0;

        if (st.isBlockStmt()) {
            removed += processBlock(st.asBlockStmt());

        } else if (st.isIfStmt()) {
            IfStmt s = st.asIfStmt();
            Statement thenS = s.getThenStmt();
            if (thenS.isBlockStmt()) removed += processBlock(thenS.asBlockStmt());
            else removed += processSingle(thenS);

            if (s.getElseStmt().isPresent()) {
                Statement elseS = s.getElseStmt().get();
                if (elseS.isBlockStmt()) removed += processBlock(elseS.asBlockStmt());
                else removed += processSingle(elseS);
            }

        } else if (st.isForStmt()) {
            Statement body = st.asForStmt().getBody();
            if (body.isBlockStmt()) removed += processBlock(body.asBlockStmt());
            else removed += processSingle(body);

        } else if (st.isForEachStmt()) {
            Statement body = st.asForEachStmt().getBody();
            if (body.isBlockStmt()) removed += processBlock(body.asBlockStmt());
            else removed += processSingle(body);

        } else if (st.isWhileStmt()) {
            Statement body = st.asWhileStmt().getBody();
            if (body.isBlockStmt()) removed += processBlock(body.asBlockStmt());
            else removed += processSingle(body);

        } else if (st.isDoStmt()) {
            Statement body = st.asDoStmt().getBody();
            if (body.isBlockStmt()) removed += processBlock(body.asBlockStmt());
            else removed += processSingle(body);

        } else if (st.isTryStmt()) {
            TryStmt ts = st.asTryStmt();
            removed += processBlock(ts.getTryBlock());
            for (CatchClause cc : ts.getCatchClauses()) {
                removed += processBlock(cc.getBody());
            }
            if (ts.getFinallyBlock().isPresent()) {
                removed += processBlock(ts.getFinallyBlock().get());
            }

        } else if (st.isSwitchStmt()) {
            SwitchStmt sw = st.asSwitchStmt();
            for (SwitchEntry entry : sw.getEntries()) {
                NodeList<Statement> list = entry.getStatements();
                for (int i = 0; i < list.size(); i++) {
                    Statement es = list.get(i);
                    if (es.isBlockStmt()) removed += processBlock(es.asBlockStmt());
                    else removed += processSingle(es);
                }
            }

        } else if (st.isSynchronizedStmt()) {
            removed += processBlock(st.asSynchronizedStmt().getBody());

        } else if (st.isLabeledStmt()) {
            removed += recurse(st.asLabeledStmt().getStatement());
        }

        // LambdaExpr only appears inside expressions; since we never descend into expressions,
        // lambdas are implicitly ignored as requested.
        return removed;
    }

    /** Scan for LambdaExpr nodes inside the statement and clean their bodies. */
    private static int processLambdasInStatement(Statement st) {
        int removed = 0;
        List<LambdaExpr> lambdas = st.findAll(LambdaExpr.class);
        for (int i = 0; i < lambdas.size(); i++) {
            LambdaExpr lam = lambdas.get(i);
            Statement body = lam.getBody();

            if (body.isBlockStmt()) {
                // Clean assertions inside lambda block body
                removed += processBlock(body.asBlockStmt());
            } else if (body.isExpressionStmt()) {
                // Single-expression lambda, e.g., x -> assertSomething(...);  (as an ExpressionStmt)
                Expression e = body.asExpressionStmt().getExpression();
                if (e instanceof MethodCallExpr) {
                    MethodCallExpr mc = (MethodCallExpr) e;
                    if (isAssertOrFail(mc)) {
                        // Replace with empty block to keep syntax valid: x -> {}
                        lam.setBody(new BlockStmt());
                        removed++; // we effectively removed that assertion statement from the lambda
                    }
                }
            }
            // If it's some other Statement form, we leave it as-is.
        }
        return removed;
    }


}
