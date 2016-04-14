package provingground
import provingground.{
  FiniteDistribution => FD, 
  TruncatedDistribution => TD, 
  ProbabilityDistribution => PD,
  TermLang => TL}

import HoTT._

/**
 * Generating terms from given ones using the main HoTT operations, and the adjoint of this generation.
 * This is viewed as deduction.
 * Generation is a map on probability distributions, 
 * but the adjoint regards truncated distributions as the tangent space, and restricts domain to finite distributions.
 * 
 */
object Deducer {
  def isFunc : Term => Boolean = {
    case _ :  FuncLike[_, _] => true
    case _ => false
  }
  
  def isTyp : Term => Boolean = {
    case _ :  Typ[_] => true
    case _ => false
  }


  /**
   * generating optionally using function application, with function and argument generated recursively;
   * to be mixed in using `<+?>`
   */
  def appln(rec: => (PD[Term] => PD[Term]))(p: PD[Term]) = 
    rec(p) flatMap ((f) =>
      if (isFunc(f)) 
        rec(p) map (TL.appln(f, _))
      else 
        FD.unif(None : Option[Term])
      )

   /**
   * generating optionally as lambdas, with function and argument generated recursively;
   * to be mixed in using `<+?>`
   */
  def lambda(varweight: Double)(rec: => (PD[Term] => PD[Term]))(p: PD[Term]) : PD[Option[Term]] = 
    rec(p) flatMap ({
      case tp: Typ[u] =>
        val x = tp.Var
        val newp = p <+> (FD.unif(x), varweight)
        (rec (newp)) map ((y: Term) => TL.lambda(x, y))
      case _ => FD.unif(None)
    }
    )
    
  /**
   * generating optionally as pi's, with function and argument generated recursively;
   * to be mixed in using `<+?>`
   */
  def pi(varweight: Double)(rec: => (PD[Term] => PD[Term]))(p: PD[Term]) : PD[Option[Term]] = 
    rec(p) flatMap ({
      case tp: Typ[u] =>
        val x = tp.Var
        val newp = p <+> (FD.unif(x), varweight)
        (rec (newp)) map ((y: Term) => TL.pi(x, y))
      case _ => FD.unif(None)
    }
    )   
  
  /**
   * returns map of inverse image under function application, 
   * i.e., for `y` in range, returns vector of pairs `(f, x)` with `f(x) = y`
   */  
  def applnInvImage(supp: Vector[Term]) =
    {
      val pairs = (supp collect{ 
        case fn: FuncLike[_, _] =>
          supp filter (_.typ == fn.dom) map ((fn : Term, _)) 
      }).flatten
      val optMap = pairs groupBy {case (f, x) => TL.appln(f, x)}
      for ((yo, fx) <- optMap; y <- yo) yield (y, fx)
    }
  
  /**
   * returns map of inverse image for a fixed function `f`, 
   * i.e., for `y` in range, returns `x` with `f(x) = y`
   */
  def funcInvImage(supp: Vector[Term])(fn: Term) : Map[Term, Vector[Term]] = fn match {
    case f: FuncLike[_, _] =>
      val optMap = supp filter (_.typ == f.dom) groupBy (TL.appln(f, _))
      for ((yo, fx) <- optMap; y <- yo) yield (y, fx)
    case _ => Map.empty
  }
  
  def argInvImage(supp: Vector[Term])(arg : Term) : Map[Term, Vector[Term]] = {
   val optMap = (supp collect {case fn: FuncLike[u, v] if fn.dom == arg.typ => fn}) groupBy (TL.appln(_, arg))
   for ((yo, fx) <- optMap; y <- yo) yield (y, fx)
  }
  
  /**
   * inverse image of a function as a finite distribution;
   * an atom of weight 1 at each inverse image point.
   */
  def invDstbn(supp: Vector[Term])(fn: Term) = 
    (y: Term) => ((funcInvImage(supp)(fn)) mapValues (FD.rawUnif(_))).getOrElse(y, FD.empty[Term])
  /**
   * inverse image of a function as a truncated distribution;
   * an atom of weight 1 at each inverse image point.
   */  
  def invTD(supp: Vector[Term])(fn: Term) : Term => TD[Term] = 
    (y: Term) => ((funcInvImage(supp)(fn)) mapValues ((xs) => TD(FD.rawUnif(xs)))).getOrElse(y, TD.Empty[Term])
  
  def argInvTD(supp: Vector[Term])(arg: Term) : Term => TD[Term] = 
    (y: Term) => ((argInvImage(supp)(arg)) mapValues ((xs) => TD(FD.rawUnif(xs)))).getOrElse(y, TD.Empty[Term])  
    
  /**
   * returns the term of the application adjoint with differentiation holding a function fixed;
   * this is the sum over all functions of the adjoint of that function application, scaled by the function weight. 
   */
  def applnAdjointOnFuncs(fd: FD[Term])(w : => TD[Term]) : TD[Term] = {
      val tds = 
        for (Weighted(f, p) <- fd.pmf if isFunc(f)) yield
          (w flatMap ((y) => invTD(fd.supp)(f)(y))) <*> p 
      TD.BigSum(tds)
    }

  def applnAdjointOnArgs(fd: FD[Term])(w : => TD[Term]) : TD[Term] = {
      val tds = 
        for (Weighted(x, p) <- fd.pmf)  yield
          (w flatMap ((y) => argInvTD(fd.supp)(x)(y))) <*> p 
      TD.BigSum(tds)
    }
    
  def applnAdjoint(recAdj : => (FD[Term] => TD[Term] => TD[Term]))(fd: FD[Term])(w : => TD[Term]) =
    recAdj(fd)(applnAdjointOnArgs(fd)(w) <+> applnAdjointOnFuncs(fd)(w))
  
  case object variable extends AnySym{
    def apply[U <: Term with Subs[U]](typ: Typ[U]) = typ.symbObj(this)
  }
   
  /**
   * given a type, returns optionally values of lambda terms with variable of the given type
   * with variable in values from the above `variable` object
   */
  def lambdaValue[U <: Term with Subs[U]](typ: Typ[U]) : Term => Option[Term] = {
    case l: LambdaLike[u, v] => 
      Some(l.value replace (l.variable, variable(l.variable.typ)))
    case _ => None
  }
  
  /**
   * given a finite distribution of terms, 
   * returns a map associating to a type the distribution of `value`s of lambda terms of that type;
   * with variable in values from the above `variable` object
   */
//  def lambdaFDMap(fd: FD[Term]) = {    
//    val pmf = (fd.pmf collect {
//      case Weighted(l: LambdaLike[u, v], p) => 
//        (l.variable.typ, l.value.replace(l.variable, variable(l.variable.typ)), p)}).
//        groupBy(_._1).
//        mapValues ((v) => v map ((z) => Weighted(z._2 : Term, z._3)))
//    pmf mapValues (FiniteDistribution(_).flatten)
//  }
  
  /**
   * given a truncated distribution of terms and a type, 
   * returns the truncated distribution of `value`s of lambda terms of that type;
   * with variable in the values from the above `variable` object
   */
  def lambdaTD(td: TD[Term]) =
    (typ: Typ[Term]) => 
      td mapOpt (lambdaValue(typ))
  
  def lambdaAdjointOnIslands(
      recAdj : => (FD[Term] => TD[Term] => TD[Term]))(
          fd: FD[Term])(w : => TD[Term]) : TD[Term]  = {
        val vec   = 
          fd.supp collect {
          case typ: Typ[u] =>
            val innerw = w mapOpt (lambdaValue(typ))
            val x = variable(typ)
            val innerp = fd mapOpt (lambdaValue(typ)) // this already is scaled by weight of type and of lambda-term
            recAdj(innerp)(innerw) map ((t) => HoTT.lambda(x)(t) : Term)
          }
        TD.BigSum(vec)
  }
      
  def lambdaAdjointCoeff(
      recAdj : => (FD[Term] => TD[Term] => TD[Term]))(
          fd: FD[Term])(w : => TD[Term]) = {
        def pmf(cutoff: Double)   = 
          fd.supp collect {
            case typ: Typ[u] =>
              val neww = lambdaTD(w)(typ)
              val x = variable(typ)
              val newp = fd mapOpt (lambdaValue(typ))
              val wfd = neww.getFD(cutoff).getOrElse(FD.empty[Term])
              Weighted(typ: Term, (wfd map ((t) => newp(t))).expectation)
    }
        def finDist(cutoff: Double) = FD(pmf(cutoff))
      TD.FromFDs(finDist)
  }
  
}

class DeducerFunc(applnWeight: Double, lambdaWeight: Double, piWeight: Double, varWeight: Double){
  import Deducer._ 
  def func(pd: PD[Term]): PD[Term] = 
    pd. 
    <+?> (appln(func)(pd), applnWeight).
    <+?> (lambda(varWeight)(func)(pd), lambdaWeight).
    <+?> (pi(varWeight)(func)(pd), lambdaWeight)
}