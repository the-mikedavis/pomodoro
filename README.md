# pomodoro

The [Pomodoro method](https://en.wikipedia.org/wiki/Pomodoro_Technique) is a strategy for avoiding mental fatigue while working for long periods of time. Normally, you set a timer for about 25 minutes, work hard, and then get a break for 3-5 minutes. Wouldn't it be great if a whole dev team could do pomodoros and be able to see who is doing them and much longer the pomodoro will be?

The `pomo` bot allows you to do just that. Here's a sample:

![invocation](https://i.imgur.com/oXdjMtu.jpg)

## Installation

As of yet, this is not a Slack App, so installing it is tricky.

1. Leiningen:
    - macOS: `brew install lein`
    - other UNIX OSes may have a similar command (e.g. `sudo apt-get install lein`)
2. Clone this repo:
    - SSH: `git clone git@github.com:the-mikedavis/pomodoro.git`
    - HTTP: `git clone https://github.com/the-mikedavis/pomodoro.git`
3. Create the api-token
    - Go [here](https://api.slack.com/custom-integrations/bot-users) and read up on adding an api-token for a bot
    - Paste the api-token as the only text in a file called `api-token.txt` in this directory.
4. Execute: `lein run` in this directory.

## Usage

Run lein.

You have 4 commands, which are just keywords. You'll need to invoke the bot by mentioning it's name (I recommend `pomo`).

## Options

- start: start a 20 minute timer
- end: kill that timer, if there is one
- status: how much time is left on your Do Not Disturb
- team: how much time is left on your whole team's Do Not Disturbs, if they are on

## Examples

An example invocation:

    Hey @pomo what's the team up to?
    Hey @pomo start my zen
    Wait no @pomo end that
    What's my status @pomo?

### Bugs

Capitalizing any of the letters of the keywords makes them unrecognizable. Regex needs to ignore case.

### TODO

- [ ] get real Slack App status
- [ ] become a Slack developer
- [ ] get bots access to Do Not Disturb controls
    - the api can be called with `(call-slack-web-api "dnd.setSnooze" {:token *api-token* :num_minutes 2})`

## License

Copyright © 2017 Michael Davis

Distributed under the Eclipse Public License version 1.0.
