/* Generated By:JJTree: Do not edit this line. ASTForVarStatement.java */

package sidekick.ecmascript.parser;

public class ASTForVarStatement extends SimpleNode {
  public ASTForVarStatement(int id) {
    super(id);
  }

  public ASTForVarStatement(EcmaScript p, int id) {
    super(p, id);
  }


  /** Accept the visitor. **/
  public Object jjtAccept(EcmaScriptVisitor visitor, Object data) {
    return visitor.visit(this, data);
  }
}
