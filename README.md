# PokeScape PvM

The companion plugin for **PokeScape PvM**, a friendly competition hosted by Soffan and Quo. This plugin automatically 
captures and submits screenshots for leveling Runemon, battling Gyms and other activities while playing PokeScape.

***Note:*** This plugin is only intended for use by competitors playing PokeScape. For more information about PokeScape 
you may join the [Discord](https://discord.com/invite/dmfF6yMV9m) or visit the [official site](https://pokescape.com/).

## User Guide

#### Team Information
The side panel displays the status of the PokeScape server, your Team Name, Total Level and Dex Count. You will need to 
log into a Runescape account that has been accepted and placed into a PokeScape team before the plugin will begin to 
function.
 
![Team Information](https://i.imgur.com/0ishhcg.png)

Once you are connected to your team, the plugin will automatically take and submit screenshots to your submission and
battle channels while you play!

![Discord Submission](https://i.imgur.com/iqI9cZX.png)

The plugin will take screenshots for Runémon your team has not yet captured. Submissions for uncaught Runémon 
will be sent to storage where the xp can be redeemed after the Runémon has been captured by using `/redeem xp`. A 
screenshot may also be sent to storage if the submission is valid for leveling multiple Runémon. Your team can use 
`/redeem xp` to pick which Runémon gets credit for the submission.

#### Minigame Verification
Some activities for leveling Runémon require additional verification checks after the start of a competition. The plugin 
currently supports verifying Tempoross permits stored in the Reward Pool and Guardians of the Rift energy stored 
at the Rewards Guardian. The Minigame Verification section in the side panel displays your current verification 
status for each activity.

![Minigame Verification](https://i.imgur.com/JpzirdV.png)

After the start of a competition, use the "Net" or "Check" options at each activity to verify the respective minigame. 
For verification to be successful you must:
* Playing on a Runescape account registered in the competition
* Logged into a normal world after the start of the competition
* Have 0 permits/energy stored at the minigame being validated
* The overlay containing the event password must be visible

A screenshot will be sent to your team channel when verification succeeds. Once you have verified a minigame, you do not 
need to verify it again. Submissions of loot from the Reward Pool or the Rewards Guardian will not be accepted and the 
plugin will not take screenshots until verification has been completed.

## Help

If you've experienced an issue with the plugin or have a recommendation on how to improve it, you may 
[create an issue](https://github.com/Zoinkwiz/quest-helper/issues/new) with the relevant details.

In addition, there's a [Discord](https://discord.com/invite/dmfF6yMV9m) you can use for discussion and raising issues.
