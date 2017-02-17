package com.a.eye.skywalking.trace;

/**
 * Span is a concept from OpenTracing Spec, also from Google Dapper Paper.
 * Traces in OpenTracing are defined implicitly by their Spans.
 *
 *               [Span A]  ←←←(the root span)
 *                  |
 *           +------+------+
 *           |             |
 *           [Span B]      [Span C] ←←←(Span C is a `ChildOf` Span A)
 *           |             |
 *           [Span D]      +---+-------+
 *                         |           |
 *                      [Span E]    [Span F] >>> [Span G] >>> [Span H]
 *                                     ↑
 *                                     ↑
 *                                     ↑
 *                                  (Span G `FollowsFrom` Span F)
 *
 * Created by wusheng on 2017/2/17.
 */
public class Span {
}
