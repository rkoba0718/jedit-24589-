/* Generated By:JJTree: Do not edit this line. ASTThisReference.java */

package sidekick.ecmascript.parser;

public class ASTThisReference extends SimpleNode {
  public ASTThisReference(int id) {
    super(id);
  }

  public ASTThisReference(EcmaScript p, int id) {
    super(p, id);
  }


  /** Accept the visitor. **/
  public Object jjtAccept(EcmaScriptVisitor visitor, Object data) {
    return visitor.visit(this, data);
  }
}