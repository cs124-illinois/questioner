import { Literal, Static, Union } from "runtypes"

export const Languages = Union(Literal("java"), Literal("kotlin"))
export type Languages = Static<typeof Languages>
