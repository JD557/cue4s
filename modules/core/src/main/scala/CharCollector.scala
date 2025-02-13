/*
 * Copyright 2023 Neandertech
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cue4s

private object CharCollector:
  enum State:
    case Init, ESC_Started, CSI_Started
    case CSI_Collecting(bytes: List[Byte])

  enum DecodeResult:
    case Continue
    case Error(msg: String)

    def toNext[R]: Next[R] = this match
      case Continue   => Next.Continue
      case Error(msg) => Next.Error(msg)

  def decode(curState: State, char: Int): (State, DecodeResult | Event) =
    def isCSIParameterByte(b: Int) =
      (b >= 0x30 && b <= 0x3f)

    def isCSIIntermediateByte(b: Int) =
      (b >= 0x20 && b <= 0x2f)

    def isCSIFinalByte(b: Int) =
      (b >= 0x40 && b <= 0x7e)

    def error(msg: String) =
      (curState, DecodeResult.Error(msg))

    def emit(event: Event) =
      (curState, event)

    def toInit(result: DecodeResult | Event) =
      (State.Init, result)

    curState match
      case State.Init =>
        char match
          case AnsiTerminal.ESC =>
            (State.ESC_Started, DecodeResult.Continue)
          case 10 | 13 =>
            emit(Event.Key(KeyEvent.ENTER))
          case 9 =>
            emit(Event.Key(KeyEvent.TAB))
          case 127 =>
            emit(Event.Key(KeyEvent.DELETE))
          case -1 =>
            error("Invalid character -1")
          case _ =>
            emit(Event.Char(char))

      case State.ESC_Started =>
        char match
          case '[' =>
            (State.CSI_Started, DecodeResult.Continue)
          case _ =>
            error(s"Unexpected symbol ${char} following an ESC sequence")

      case State.CSI_Started =>
        char match
          case 'A' => toInit(Event.Key(KeyEvent.UP))
          case 'B' => toInit(Event.Key(KeyEvent.DOWN))
          case 'C' => toInit(Event.Key(KeyEvent.RIGHT))
          case 'D' => toInit(Event.Key(KeyEvent.LEFT))

          case b
              if isCSIParameterByte(b) || isCSIIntermediateByte(
                b,
              ) =>
            (State.CSI_Collecting(b.toByte :: Nil), DecodeResult.Continue)

      case State.CSI_Collecting(bytes) =>
        char match
          case b if isCSIFinalByte(b) =>
            toInit(Event.CSICode(bytes))
          case _ =>
            error(s"Unexpected byte ${char}, expected CSI final byte")

    end match
  end decode

end CharCollector
