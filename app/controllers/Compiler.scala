package controllers

import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter.{IMain, ILoop}
import java.io.File

/**
 * @author sameer
 */

object OutputFormats extends Enumeration {
  val string, html, javascript = Value
  implicit def outputFormatToString(f: Value): String = f.toString
  implicit def stringToOutputFormat(s: String): Value = withName(s)
}

import OutputFormats._

case class Input(code: String, outputFormat: String = OutputFormats.html, extraFields: Map[String, String] = Map.empty)

case class Result(result: String, format: String = OutputFormats.html)

/**
 * A Wolfenstein Compiler
 */
trait Compiler {
  // name of the compiler that should be unique in a collection of compilers
  def name: String

  // code to construct the editor for a cell of this type
  def editorJavascript: String

  // whether to hide the editor after compilation or not (essentially replacing editor with the output)
  def hideAfterCompile: Boolean = true

  // code that compiles an input to output, in Scala
  def compile(input: Input): Result

  // javascript that extracts the code from the editor and creates a default input
  def editorToInput: String

  // icon that is used in the toolbar
  def toolbarIcon: String = name(0).toUpper + name.drop(1)

  def start = {}
}

/**
 * Compiler where the editor is a basic ACE editor
 */
trait ACEEditor {
  this: Compiler =>
  def editorMode: String = name

  def outputFormat: OutputFormats.Value = OutputFormats.html

  def editorJavascript: String =
    """
      |function(id) {
      |    var editor = ace.edit("editor"+id);
      |    editor.setTheme("ace/theme/solarized_light");
      |    editor.getSession().setMode("ace/mode/%s");
      |    editor.focus();
      |    editor.navigateFileEnd();
      |    editor.setBehavioursEnabled(false);
      |
      |    heightUpdateFunction(editor, '#editor'+id);
      |    editor.getSession().on('change', function () {
      |        heightUpdateFunction(editor, '#editor'+id);
      |    });
      |
      |    editor.commands.addCommand({
      |        name: "runCode",
      |        bindKey: {win: "Ctrl-Enter", mac: "Ctrl-Enter"},
      |        exec: function(editor) {
      |            document.getElementById("runCode"+id).click();
      |        }
      |    })
      |    return editor;
      |}
    """.stripMargin format(editorMode)

  // javascript that extracts the code from the editor and creates a default input
  def editorToInput: String =
    """
      |function (doc, id) {
      |  input = {}
      |  input.code = doc.cells[id].editor.getSession().getValue();
      |  input.outputFormat = "%s";
      |  return input;
      |};
    """.stripMargin format(outputFormat)
}

/* --------------------------------------------------
 *                   COMPILERS
 * --------------------------------------------------
 */
class HTMLCompiler extends Compiler with ACEEditor {
  def name: String = "html"

  // icon that is used in the toolbar
  override def toolbarIcon: String = "&lt;html&gt;"

  def compile(input: Input): Result = {
    assert(input.outputFormat equalsIgnoreCase outputFormat)
    Result(input.code, outputFormat)
  }
}

class HeadingCompiler(val level: Int) extends Compiler with ACEEditor {
  def name: String = "heading" + level

  override def editorMode: String = "text"

  // icon that is used in the toolbar
  override def toolbarIcon: String = "<span class=\"glyphicon glyphicon-header\">%d</span>" format (level)

  def compile(input: Input): Result = {
    assert(input.outputFormat equalsIgnoreCase outputFormat)
    Result("<h%d>%s</h%d>" format(level, input.code, level), outputFormat)
  }
}

class ImageURLCompiler extends Compiler with ACEEditor {
  def name: String = "imageurl"

  override def editorMode: String = "text"

  // icon that is used in the toolbar
  override def toolbarIcon: String = "<i class=\"fa fa-picture-o\"></i>"

  def compile(input: Input): Result = {
    assert(input.outputFormat equalsIgnoreCase outputFormat)
    Result("<img src=\"%s\" class=\"img-thumbnail displayed\" />" format (input.code), outputFormat)
  }
}

/**
 * Basic markdown compiler using Actuarius
 */
class ActuriusCompiler extends Compiler with ACEEditor {

  import eu.henkelmann.actuarius.ActuariusTransformer

  def name = "markdown"

  // icon that is used in the toolbar
  override def toolbarIcon: String = "&Mu;d"

  val transformer = new ActuariusTransformer()

  def compile(input: Input) = {
    assert(input.outputFormat equalsIgnoreCase outputFormat)
    Result(transformer(input.code), outputFormat)
  }
}

class LatexCompiler extends Compiler with ACEEditor {
  def name = "latex"

  // icon that is used in the toolbar
  override def toolbarIcon: String = "<i class=\"fa fa-superscript\"></i>"

  def compile(input: Input) = {
    assert(input.outputFormat equalsIgnoreCase outputFormat)
    Result("$$" + input.code + "$$", outputFormat)
  }

}

/**
 * Scala server using the Twitter Eval implementation
 */
class TwitterEvalServer extends Compiler with ACEEditor {

  def name = "scala"

  override def outputFormat: OutputFormats.Value = OutputFormats.string

  // whether to hide the editor after compilation or not (essentially replacing editor with the output)
  override def hideAfterCompile: Boolean = false

  def compile(input: Input) = {
    assert(input.outputFormat equalsIgnoreCase outputFormat)
    val code = input.code;
    val eval = new Evaluator(None) // List("/Users/sameer/src/research/interactiveppl/lib/scalapplcodefest_2.10-0.1.0.jar"))
    println("compiling code : " + code)
    val result = try {
      eval.apply[Any](code, false)
    } catch {
      case e: CompilerException => e.m.mkString("\n\t")
    } finally {
      "Compile Error!!"
    }
    println("result: " + result)
    Result(result.toString, outputFormat)
  }
}

class GoogleDocsViewer extends Compiler with ACEEditor {
  // name of the compiler that should be unique in a collection of compilers
  def name: String = "google_viewer"

  // icon that is used in the toolbar
  override def toolbarIcon: String = "<i class=\"fa fa-eye\"></i>"

  override def editorMode: String = "text"

  def compile(input: Input) = {
    assert(input.outputFormat equalsIgnoreCase outputFormat)
    Result(
      "<iframe src=\"http://docs.google.com/viewer?url=http://infolab.stanford.edu/pub/papers/google.pdf&embedded=true\" style=\"width:800px; height:600px;\" frameborder=\"0\"></iframe>",
      outputFormat)
  }
}