package vscextension

class agdaParsingSuite extends munit.FunSuite {
  test("finds holes and preserves their ranges and contents") {
    val source = "f = ?\ng = {}\nh = {! x !}"
    val question = source.indexOf('?')
    val braces = source.indexOf("{}")
    val existing = source.indexOf("{!")

    assertEquals(
      agdaParsing.findHoles(source),
      Vector(
        agdaParsing.Hole(question, question + 1, "", braced = false),
        agdaParsing.Hole(braces, braces + 2, "", braced = true),
        agdaParsing.Hole(existing, existing + 7, "x", braced = true)
      )
    )
  }

  test("normalizes placeholders outside comments and strings") {
    val source = "-- ? and {}\n{- ? {- {} -} -}\n\"? {}\" ? {} {! keep ? and {} !}"
    val expected = "-- ? and {}\n{- ? {- {} -} -}\n\"? {}\" {! !} {! !} {! keep ? and {} !}"

    assertEquals(agdaParsing.normalizeHoleSyntax(source), expected)
  }

  test("parses Agda status and annotation responses") {
    assertEquals(
      agdaParsing.parseEvent("(agda2-status-action \"Checked,Warnings\")"),
      Some(agdaParsing.ParsedResponse(None, Some(agdaParsing.StatusEvent(checked = true))))
    )
    assertEquals(
      agdaParsing.parseEvent("(agda2-highlight-add-annotations add (1 4 (data . datatype)))"),
      Some(
        agdaParsing.ParsedResponse(
          Some(false -> Vector(agdaParsing.Highlight(1, 4, "type"))),
          None
        )
      )
    )
  }

  test("parses goal and case-split events") {
    assertEquals(
      agdaParsing.parseEvent("(agda2-goals-action (2 5))"),
      Some(agdaParsing.ParsedResponse(None, Some(agdaParsing.GoalIndicesEvent(Vector(2, 5)))))
    )
    assertEquals(
      agdaParsing.parseEvent("(agda2-make-case-action (\"x = zero\" \"x = suc n\"))"),
      Some(
        agdaParsing.ParsedResponse(
          None,
          Some(agdaParsing.MakeCaseEvent(Vector("x = zero", "x = suc n")))
        )
      )
    )
  }

  test("parses Give event variants and rejects invalid goal indices") {
    assertEquals(
      agdaParsing.parseEvent("(agda2-give-action 3 paren)"),
      Some(agdaParsing.ParsedResponse(None, Some(agdaParsing.GiveParenEvent(3))))
    )
    assertEquals(
      agdaParsing.parseEvent("(agda2-give-action 4 \"no-paren\")"),
      Some(agdaParsing.ParsedResponse(None, Some(agdaParsing.GiveNoParenEvent(4))))
    )
    assertEquals(
      agdaParsing.parseEvent("(agda2-give-action 5 \"suc n\")"),
      Some(agdaParsing.ParsedResponse(None, Some(agdaParsing.GiveStringEvent(5, "suc n"))))
    )
    assertEquals(agdaParsing.parseEvent("(agda2-give-action nope paren)"), None)
  }

  test("stream parser buffers partial expressions and respects quoted parentheses") {
    val stream = new agdaParsing.SexpStream
    val firstChunk = "(agda2-info-action \"title\" \"message ("
    val secondChunk = "nested)\")"

    assertEquals(stream.feed(firstChunk), Nil)
    val complete = stream.feed(secondChunk)
    assertEquals(complete, List(firstChunk + secondChunk))
    assertEquals(
      complete.flatMap(agdaParsing.parseEvent),
      List(agdaParsing.ParsedResponse(None, Some(agdaParsing.InfoEvent("title", "message (nested)"))))
    )
  }

  test("converts Agda code-point offsets to UTF-16 offsets") {
    assertEquals(agdaParsing.toUtf16Offset("a\r\n😀b", 2), 3)
    assertEquals(agdaParsing.toUtf16Offset("a\r\n😀b", 3), 5)
  }
}
