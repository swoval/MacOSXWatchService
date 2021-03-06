package com.swoval.test

import scala.reflect.macros.blackbox
import scala.util.Properties

object Macros {
  def testOnWithDesc(c: blackbox.Context)(desc: c.Expr[String], platforms: c.Expr[Platform]*)(
      tests: c.Expr[Any]): c.Tree = {
    impl(c)(reifiedPlatforms(c)(platforms), desc, tests)
  }
  def testOn(c: blackbox.Context)(platforms: c.Expr[Platform]*)(tests: c.Expr[Any]): c.Tree = {
    import c.universe._
    q"""
      com.swoval.test.testOn(this.getClass.getName.replaceAll("\\$$", ""), ..$platforms)($tests)
    """
  }

  private def ignore(c: blackbox.Context)(context: c.Expr[String], p: Platform): c.Tree = {
    import c.universe._
    q"""
      Tests {
        'ignore - {
          println(${Literal(Constant("Not running "))} + $context + ${Literal(Constant(s" on $p"))})
        }
      }
    """
  }
  private def impl(c: blackbox.Context)(platforms: Seq[Platform],
                                        context: c.Expr[String],
                                        tests: c.Expr[Any]): c.Tree = {
    import c.universe._
    val thisPlatform = if (Properties.isMac) MacOS else Linux
    if (platforms.contains(thisPlatform)) {
      q"Tests { ..$tests }"
    } else ignore(c)(context, thisPlatform)
  }
  private def reifiedPlatforms(c: blackbox.Context)(platforms: Seq[c.Expr[Platform]]) = {
    import c.universe._
    platforms.view.map(_.tree) map {
      case Select(_, TermName("MacOS")) => MacOS
      case Select(_, TermName("Linux")) => Linux
      case p                            => c.abort(c.enclosingPosition, s"Unknown platform $p")
    }
  }
}
