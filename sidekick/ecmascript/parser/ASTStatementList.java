/* Generated By:JJTree: Do not edit this line. ASTStatementList.java */

package sidekick.ecmascript.parser;

public class ASTStatementList extends SimpleNode {
  public ASTStatementList(int id) {
    super(id);
  }

  public ASTStatementList(EcmaScript p, int id) {
    super(p, id);
  }


  /** Accept the visitor. **/
  public Object jjtAccept(EcmaScriptVisitor visitor, Object data) {
    return visitor.visit(this, data);
  }
}