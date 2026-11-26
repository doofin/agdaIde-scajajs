package vscextension

object agdaParsing {
  sealed trait ServerEvent
  case class StatusEvent(checked: Boolean) extends ServerEvent
  case class InfoEvent(title: String, message: String) extends ServerEvent
  case class GoalIndicesEvent(indices: Vector[Int]) extends ServerEvent
  case class MakeCaseEvent(lines: Vector[String]) extends ServerEvent
  case class GiveParenEvent(index: Int) extends ServerEvent
  case class GiveNoParenEvent(index: Int) extends ServerEvent
  case class GiveStringEvent(index: Int, content: String) extends ServerEvent

  case class GoalLocation(index: Int, start: Int, end: Int, content: String, braced: Boolean)
  case class Hole(start: Int, end: Int, content: String, braced: Boolean)
  case class Highlight(start: Int, end: Int, tokenType: String)
  case class ParsedResponse(
      highlights: Option[(Boolean, Vector[Highlight])],
      serverEvent: Option[ServerEvent]
  )

  private sealed trait Sexp
  private case class Atom(value: String) extends Sexp
  private case class Text(value: String) extends Sexp
  private case class ListValue(values: List[Sexp]) extends Sexp

  val tokenTypeNames = Seq(
    "namespace",
    "class",
    "enumMember",
    "type",
    "parameter",
    "variable",
    "property",
    "function",
    "macro",
    "comment",
    "string",
    "keyword",
    "number",
    "operator"
  )

  val tokenTypeIndex = tokenTypeNames.zipWithIndex.toMap

  private val aspectTypes = Map(
    "module" -> "namespace",
    "record" -> "class",
    "inductiveconstructor" -> "enumMember",
    "coinductiveconstructor" -> "enumMember",
    "datatype" -> "type",
    "primitivetype" -> "type",
    "argument" -> "parameter",
    "bound" -> "variable",
    "generalizable" -> "variable",
    "field" -> "property",
    "function" -> "function",
    "postulate" -> "function",
    "primitive" -> "type",
    "macro" -> "macro",
    "comment" -> "comment",
    "string" -> "string",
    "keyword" -> "keyword",
    "number" -> "number",
    "operator" -> "operator"
  )

  private val responseTags = Set(
    "agda2-highlight-clear",
    "agda2-highlight-add-annotations",
    "agda2-status-action",
    "agda2-info-action",
    "agda2-info-action-and-copy",
    "agda2-goals-action",
    "agda2-make-case-action",
    "agda2-make-case-action-extendlam",
    "agda2-give-action"
  )

  final class SexpStream {
    private var pending = ""

    def clear(): Unit = pending = ""

    def feed(chunk: String): List[String] = {
      val input = pending + chunk
      val expressions = List.newBuilder[String]
      var start = -1
      var depth = 0
      var inString = false
      var escaped = false
      var index = 0

      while (index < input.length) {
        val char = input.charAt(index)
        if (start < 0) {
          if (char == '(') {
            start = index
            depth = 1
          }
        } else if (inString) {
          if (escaped) escaped = false
          else if (char == '\\') escaped = true
          else if (char == '"') inString = false
        } else {
          if (char == '"') inString = true
          else if (char == '(') depth += 1
          else if (char == ')') {
            depth -= 1
            if (depth == 0) {
              expressions += input.substring(start, index + 1)
              start = -1
            }
          }
        }
        index += 1
      }

      pending = if (start >= 0) input.substring(start) else ""
      expressions.result()
    }
  }

  private final class SexpParser(input: String) {
    private var index = 0

    def parse(): Option[Sexp] = parseValue()

    private def skipSpaceAndQuote(): Unit =
      while (
        index < input.length &&
        (input.charAt(index).isWhitespace || input.charAt(index) == '\'')
      ) {
        index += 1
      }

    private def parseValue(): Option[Sexp] = {
      skipSpaceAndQuote()
      if (index >= input.length) None
      else
        input.charAt(index) match {
          case '(' => parseList()
          case '"' => parseText()
          case ')' => None
          case _   => parseAtom()
        }
    }

    private def parseList(): Option[Sexp] = {
      index += 1
      val values = List.newBuilder[Sexp]
      skipSpaceAndQuote()
      while (index < input.length && input.charAt(index) != ')') {
        val before = index
        parseValue() match {
          case Some(value) => values += value
          case None        => index = before + 1
        }
        skipSpaceAndQuote()
      }
      if (index < input.length) index += 1
      Some(ListValue(values.result()))
    }

    private def parseText(): Option[Sexp] = {
      index += 1
      val value = new StringBuilder
      var closed = false
      while (index < input.length && !closed) {
        val char = input.charAt(index)
        index += 1
        if (char == '"') closed = true
        else if (char == '\\' && index < input.length) {
          val escaped = input.charAt(index)
          index += 1
          value.append(
            escaped match {
              case 'n'   => '\n'
              case 'r'   => '\r'
              case 't'   => '\t'
              case other => other
            }
          )
        } else value.append(char)
      }
      if (closed) Some(Text(value.toString)) else None
    }

    private def parseAtom(): Option[Sexp] = {
      val start = index
      while (
        index < input.length &&
        !input.charAt(index).isWhitespace &&
        input.charAt(index) != '(' &&
        input.charAt(index) != ')'
      ) {
        index += 1
      }
      if (index == start) None else Some(Atom(input.substring(start, index)))
    }
  }

  private def atoms(value: Sexp): List[String] = value match {
    case Atom(atom)       => List(atom.toLowerCase)
    case Text(_)          => Nil
    case ListValue(items) => items.flatMap(atoms)
  }

  private def parseHighlight(value: Sexp): Option[Highlight] = value match {
    case ListValue(Atom(start) :: Atom(end) :: aspects :: _) =>
      for {
        startOffset <- start.toIntOption
        endOffset <- end.toIntOption
        tokenType <- atoms(aspects).iterator.flatMap(aspectTypes.get).take(1).toList.headOption
        if endOffset > startOffset
      } yield Highlight(startOffset, endOffset, tokenType)
    case _ => None
  }

  private def responseList(value: Sexp): Option[List[Sexp]] = value match {
    case ListValue(items @ Atom(tag) :: _) if responseTags.contains(tag) => Some(items)
    case ListValue(items) =>
      items.iterator.map(responseList).collectFirst { case Some(response) => response }
    case _ => None
  }

  private def integerAtoms(value: Sexp): List[Int] = value match {
    case Atom(atom)       => atom.toIntOption.toList
    case ListValue(items) => items.flatMap(integerAtoms)
    case Text(_)          => Nil
  }

  private def strings(value: Sexp): List[String] = value match {
    case Atom(atom) if atom != "." => List(atom)
    case Text(text)                => List(text)
    case ListValue(items)          => items.flatMap(strings)
    case _                         => Nil
  }

  def parseEvent(expression: String): Option[ParsedResponse] =
    new SexpParser(expression).parse().flatMap(responseList) match {
      case Some(Atom("agda2-highlight-clear") :: _) =>
        Some(ParsedResponse(Some(false -> Vector.empty), None))
      case Some(Atom("agda2-highlight-add-annotations") :: mode :: values) =>
        val replace = mode == Atom("remove")
        val tokens = values.flatMap(parseHighlight).toVector
        Some(ParsedResponse(Some(replace -> tokens), None))
      case Some(Atom("agda2-status-action") :: Text(status) :: _) =>
        Some(ParsedResponse(None, Some(StatusEvent(status.split(",").contains("Checked")))))
      case Some(Atom(tag) :: Text(title) :: Text(message) :: _)
          if tag == "agda2-info-action" || tag == "agda2-info-action-and-copy" =>
        Some(ParsedResponse(None, Some(InfoEvent(title, message))))
      case Some(Atom("agda2-goals-action") :: goals :: _) =>
        Some(ParsedResponse(None, Some(GoalIndicesEvent(integerAtoms(goals).toVector))))
      case Some(Atom("agda2-make-case-action") :: cases :: _) =>
        Some(ParsedResponse(None, Some(MakeCaseEvent(strings(cases).toVector))))
      case Some(Atom("agda2-make-case-action-extendlam") :: cases :: _) =>
        Some(ParsedResponse(None, Some(MakeCaseEvent(strings(cases).toVector))))
      case Some(Atom("agda2-give-action") :: Atom(index) :: Atom("paren") :: _) =>
        index.toIntOption.map(i => ParsedResponse(None, Some(GiveParenEvent(i))))
      case Some(Atom("agda2-give-action") :: Atom(index) :: Text("paren") :: _) =>
        index.toIntOption.map(i => ParsedResponse(None, Some(GiveParenEvent(i))))
      case Some(Atom("agda2-give-action") :: Atom(index) :: Atom("no-paren") :: _) =>
        index.toIntOption.map(i => ParsedResponse(None, Some(GiveNoParenEvent(i))))
      case Some(Atom("agda2-give-action") :: Atom(index) :: Text("no-paren") :: _) =>
        index.toIntOption.map(i => ParsedResponse(None, Some(GiveNoParenEvent(i))))
      case Some(Atom("agda2-give-action") :: Atom(index) :: Atom(content) :: _) =>
        index.toIntOption.map(i => ParsedResponse(None, Some(GiveStringEvent(i, content))))
      case Some(Atom("agda2-give-action") :: Atom(index) :: Text(content) :: _) =>
        index.toIntOption.map(i => ParsedResponse(None, Some(GiveStringEvent(i, content))))
      case _ => None
    }

  def findHoles(text: String): Vector[Hole] = {
    val result = Vector.newBuilder[Hole]
    var index = 0
    while (index < text.length) {
      if (text.startsWith("--", index)) {
        val lineEnd = text.indexOf('\n', index)
        index = if (lineEnd < 0) text.length else lineEnd + 1
      } else if (text.startsWith("{-", index)) {
        var depth = 1
        index += 2
        while (index < text.length && depth > 0) {
          if (text.startsWith("{-", index)) {
            depth += 1
            index += 2
          } else if (text.startsWith("-}", index)) {
            depth -= 1
            index += 2
          } else index += 1
        }
      } else if (text.charAt(index) == '"') {
        index += 1
        var escaped = false
        var closed = false
        while (index < text.length && !closed) {
          val char = text.charAt(index)
          index += 1
          if (escaped) escaped = false
          else if (char == '\\') escaped = true
          else if (char == '"') closed = true
        }
      } else if (text.startsWith("{!", index)) {
        val contentStart = index + 2
        val close = text.indexOf("!}", contentStart)
        if (close < 0) return result.result()
        result += Hole(index, close + 2, text.substring(contentStart, close).trim, true)
        index = close + 2
      } else if (text.startsWith("{}", index)) {
        result += Hole(index, index + 2, "", true)
        index += 2
      } else if (text.charAt(index) == '?') {
        result += Hole(index, index + 1, "", false)
        index += 1
      } else index += 1
    }
    result.result()
  }

  def normalizeHoleSyntax(text: String): String = {
    val replaceable = findHoles(text).filter { hole =>
      !hole.braced || (hole.end - hole.start == 2 && text.startsWith("{}", hole.start))
    }
    if (replaceable.isEmpty) text
    else {
      val (segments, previousEnd) = replaceable.foldLeft((Vector.empty[String], 0)) {
        case ((parts, previousEnd), hole) =>
          (parts ++ Vector(text.substring(previousEnd, hole.start), "{! !}"), hole.end)
      }
      (segments :+ text.substring(previousEnd)).mkString
    }
  }

  def toUtf16Offset(text: String, codePointOffset: Int): Int = {
    val target = math.max(0, codePointOffset)
    var codePoints = 0
    var offset = 0
    while (offset < text.length && codePoints < target) {
      val char = text.charAt(offset)
      if (char == '\r' && offset + 1 < text.length && text.charAt(offset + 1) == '\n') {
        offset += 2
      } else if (
        Character.isHighSurrogate(char) &&
        offset + 1 < text.length &&
        Character.isLowSurrogate(text.charAt(offset + 1))
      ) {
        offset += 2
      } else offset += 1
      codePoints += 1
    }
    offset
  }
}
