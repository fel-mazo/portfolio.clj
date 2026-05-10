---
{:title "Designing API boundaries that age well"
 :slug "designing-api-boundaries-that-age-well"
 :locale "en"
 :date "2026-03-30"
 :date-label "March 30, 2026"
 :category "API Design"
 :tags ["APIs" "Maintainability"]
 :excerpt "Most APIs start clean. The interesting question is what happens eighteen months later, when three teams depend on your endpoint and nobody remembers why that field is nullable."}
---

## Introduction

Every API starts out looking reasonable. You've got a handful of endpoints, a small team, and the luxury of making coherent decisions. The design is clean because the world hasn't happened to it yet.

The hard part comes later. A year in, there are consumers you didn't plan for. Someone built a dashboard on a field you thought was internal. A partner integration relies on a behavior you considered a bug. The question stops being "how do I design a nice API?" and becomes "how do I change this thing without breaking everything around it?"

I don't have a framework for this. What I have are a few habits that have helped.

## Stable nouns outlast clever verbs

The most consequential naming decision in an API is usually the resource names. These nouns become the vocabulary that product managers, support teams, and other engineers use to talk about the system. Once they spread, changing them is brutally expensive.

I've learned to spend more time on nouns than on anything else. A verb-heavy endpoint like `POST /activate-subscription` locks you into a specific workflow. A noun-based `POST /subscriptions/{id}/activations` lets the workflow evolve without the name becoming a lie.

This isn't about REST purity. It's about giving yourself room. The names you choose will outlive your current understanding of the product.

## Make state transitions visible

The thing that causes the most confusion between backend and frontend teams, in my experience, is implicit state changes. An order that quietly moves from "pending" to "processing" somewhere inside a background job, with no explicit transition in the API, will generate support tickets.

I try to make every meaningful state change something a consumer can see and reason about. That usually means:

- An explicit endpoint or action for each transition
- A timestamp for when it happened
- A reason field for transitions that can fail or be reversed

This feels like overengineering until the first time someone needs to debug why an order is stuck in a weird state. Then it feels like the bare minimum.

## Design for the person who gets paged

API design conversations tend to focus on the consumer — the developer calling your endpoint. But there's another audience that rarely gets a seat at the table: the person debugging your system at midnight.

Good APIs make that person's life easier. That means clear, consistent identifiers across endpoints. Error responses that include enough context to narrow down the problem. Lifecycle events that show up in logs without requiring custom instrumentation.

I think of this as operational empathy. The consumer needs a pleasant integration experience. The operator needs to understand what went wrong, fast, without calling the person who built it.

> A maintainable API is one that still makes sense when the original authors are no longer in the room.

## Conclusion

The API boundaries that hold up over time are rarely the clever ones. They're the ones where someone thought about the names, made the state changes visible, and considered what happens when things go wrong. None of this is flashy work, but it's the kind that pays compound interest.
