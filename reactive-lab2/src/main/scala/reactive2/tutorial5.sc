package reactive2

object tutorial5 {

  ///////////////////////////////////////
  // Case classes and Pattern matching //
  ///////////////////////////////////////

  trait Expr
  case class Number(n: Int) extends Expr
  case class Sum(e1: Expr, e2: Expr) extends Expr

  // Case classes are special classes:
  // 1. The compiler automatically generates a companion object with method "apply"
  //      object Number {
  //          def apply(n: Int) = new Number(n)
  //      }
  // 2. Parameters of the constructor are automatically members of the class
  // 3. Immutable by default
  // 4. Automatically defined getters
  // 5. Default implementations of toString(), equals()
  // 6. Automatically serializable
  // 7. Can be used in pattern matching
  
  // Case classes are useful for defining data structures

  // Pattern matching is a kind of "generalized switch"
  // We can match even objects: matching "decomposes" an object:
  // - what class was used to construct the object?
  // - what values were passed to the constructor?
  def eval(e: Expr): Int = e match {
    case Number(n) => n
    case Sum(e1, e2) => eval(e1) + eval(e2)
  }                                               //> eval: (e: reactive2.tutorial5.Expr)Int
  
  val e = Sum(Sum(Number(2), Number(3)), Number(4))
                                                  //> e  : reactive2.tutorial5.Sum = Sum(Sum(Number(2),Number(3)),Number(4))
  eval(e)                                         //> res0: Int = 9
  
  // Other examples of patterns:
  //   case 1                             -> matches constant "1"
  //   case Sum(Number(1), Number(_))     -> matches object with specific structure ("_" is a wildcard)
  //   case n: Int                        -> matches any Int value
  //   case _                             -> matches anything
  
}
