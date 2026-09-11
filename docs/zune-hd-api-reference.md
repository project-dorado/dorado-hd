# Zune HD API reference (recovered exports)

Curated index of the named exports recovered from the reconstructed Zune HD modules
(`scripts/ghidra_corpus.py`, rizin export tables). Addresses are the module virtual
addresses (image base included) as they appear in the disassembly corpus.

This is the interface surface Dorado-HD mirrors or emulates; it is synthesized
metadata, not Microsoft source. Raw decompilation stays out-of-tree.

## `xuidll.dll` — 529 exports

**XUIAllocatedArray** (2)

| Export | VA |
|---|---|
| `XUIAllocatedArray_Allocate` | `0x4181A760` |
| `XUIAllocatedArray_Free` | `0x4181A7CC` |

**XUIElementPropVal** (27)

| Export | VA |
|---|---|
| `XUIElementPropVal_Construct` | `0x4181A8F8` |
| `XUIElementPropVal_ConstructFromInt` | `0x4181A908` |
| `XUIElementPropVal_ConstructFromUint` | `0x4181A918` |
| `XUIElementPropVal_ConstructFromFloat` | `0x4181A928` |
| `XUIElementPropVal_ConstructFromD3DXVECTOR` | `0x4181A94C` |
| `XUIElementPropVal_Reset` | `0x4181A970` |
| `XUIElementPropVal_SetBool` | `0x4181A988` |
| `XUIElementPropVal_SetInt` | `0x4181A9BC` |
| `XUIElementPropVal_SetUint` | `0x4181A9F0` |
| `XUIElementPropVal_SetFloat` | `0x4181AA24` |
| `XUIElementPropVal_SetColorFromUint` | `0x4181AA6C` |
| `XUIElementPropVal_SetColorFromARGB` | `0x4181AAA0` |
| `XUIElementPropVal_SetString` | `0x4181AAEC` |
| `XUIElementPropVal_SetD3DVECTOR` | `0x4181AB84` |
| `XUIElementPropVal_SetD3DVECTOR3` | `0x4181AB84` |
| `XUIElementPropVal_SetXUIQuaternion` | `0x4181ABD0` |
| `XUIElementPropVal_SetIXUIPropObj` | `0x4181AC24` |
| `XUIElementPropVal_SetCustom` | `0x4181AC58` |
| `XUIElementPropVal_Copy` | `0x4181AC94` |
| `XUIElementPropVal_GetStringLen` | `0x4181AD64` |
| `XUIElementPropVal_IsEqual` | `0x4181AE14` |
| `XUIElementPropVal_Clear` | `0x4181AFB8` |
| `XUIElementPropVal_Destruct` | `0x4181AFB8` |
| `XUIElementPropVal_ConstructFromD3DXQUATERNION` | `0x4181AFE4` |
| `XUIElementPropVal_SetD3DXQUATERNION` | `0x4181B010` |
| `XUIElementPropVal_ToString` | `0x4181B064` |
| `XUIElementPropVal_ToBinary` | `0x4181B44C` |

**XUIKeyFrame** (6)

| Export | VA |
|---|---|
| `XUIKeyFrame_Destruct` | `0x4181B5D8` |
| `XUIKeyFrame_SetPropVals` | `0x4181B5DC` |
| `XUIKeyFrame_DeleteArray` | `0x4181B5E4` |
| `XUIKeyFrame_Construct` | `0x4181B650` |
| `XUIKeyFrame_AllocArray` | `0x4181B68C` |
| `XUIKeyFrame_CopyArray` | `0x4181B750` |

**XUIKeyframeData** (5)

| Export | VA |
|---|---|
| `XUIKeyframeData_Construct` | `0x4181B898` |
| `XUIKeyframeData_Reset` | `0x4181B898` |
| `XUIKeyframeData_Detach` | `0x4181B8C0` |
| `XUIKeyframeData_CopyTo` | `0x4181B924` |
| `XUIKeyframeData_Destruct` | `0x4181BAC0` |

**XUINamedFrame** (2)

| Export | VA |
|---|---|
| `XUINamedFrame_Destruct` | `0x4181BB28` |
| `XUINamedFrame_Construct` | `0x4181BB68` |

**XUINamedframeData** (6)

| Export | VA |
|---|---|
| `XUINamedframeData_DetachInto` | `0x4181BB30` |
| `XUINamedframeData_Construct` | `0x4181BB68` |
| `XUINamedframeData_Reset` | `0x4181BB68` |
| `XUINamedframeData_Clear` | `0x4181BB80` |
| `XUINamedframeData_Destruct` | `0x4181BB80` |
| `XUINamedframeData_CopyTo` | `0x4181BBA8` |

**XUIObjectData** (5)

| Export | VA |
|---|---|
| `XUIObjectData_Construct` | `0x4181BC74` |
| `XUIObjectData_GetId` | `0x4181BDEC` |
| `XUIObjectData_Release` | `0x4181BE50` |
| `XUIObjectData_Destruct` | `0x4181BE98` |
| `XUIObjectData_AddRef` | `0x4185DDE0` |

**XUIPropertyData** (3)

| Export | VA |
|---|---|
| `XUIPropertyData_Construct` | `0x4181C138` |
| `XUIPropertyData_Reset` | `0x4181C138` |
| `XUIPropertyData_Destruct` | `0x4181C30C` |

**XUISubtimeline** (13)

| Export | VA |
|---|---|
| `XUISubtimeline_Play` | `0x4181C7A8` |
| `XUISubtimeline_IsStopped` | `0x4181C7EC` |
| `XUISubtimeline_GetAnimLength` | `0x4181C824` |
| `XUISubtimeline_Reset` | `0x4181CB04` |
| `XUISubtimeline_SetKeyFrames` | `0x4181CB54` |
| `XUISubtimeline_MakeKeyFrameOwner` | `0x4181CBD4` |
| `XUISubtimeline_GotoFrame` | `0x4181D450` |
| `XUISubtimeline_GotoFrameInterp` | `0x4181D7B4` |
| `XUISubtimeline_Clear` | `0x4181D7F4` |
| `XUISubtimeline_Construct` | `0x4181DB20` |
| `XUISubtimeline_Destruct` | `0x4181DB24` |
| `XUISubtimeline_Detach` | `0x4181DB28` |
| `XUISubtimeline_Run` | `0x4181DBB0` |

**XUISubtimelineData** (5)

| Export | VA |
|---|---|
| `XUISubtimelineData_Construct` | `0x4181C3E0` |
| `XUISubtimelineData_SetId` | `0x4181C408` |
| `XUISubtimelineData_Clear` | `0x4181C4EC` |
| `XUISubtimelineData_Destruct` | `0x4181C5D8` |
| `XUISubtimelineData_CopyTo` | `0x4181C5DC` |

**XUITimeline** (17)

| Export | VA |
|---|---|
| `XUITimeline_Construct` | `0x4181C82C` |
| `XUITimeline_FindNamedFrame` | `0x4181C88C` |
| `XUITimeline_FindNextNamedFrameAtTime` | `0x4181C8E0` |
| `XUITimeline_Stop` | `0x4181C9B4` |
| `XUITimeline_Play` | `0x4181C9C0` |
| `XUITimeline_GetAnimLength` | `0x4181CA70` |
| `XUITimeline_IsStopped` | `0x4181CA78` |
| `XUITimeline_UpdateAnimLength` | `0x4181D864` |
| `XUITimeline_GotoFrame` | `0x4181D8AC` |
| `XUITimeline_GotoFrameInterp` | `0x4181D954` |
| `XUITimeline_NotifyOwner` | `0x4181DA04` |
| `XUITimeline_Run` | `0x4181DC38` |
| `XUITimeline_SetSubtimeline` | `0x4181E1E4` |
| `XUITimeline_ClearTimelines` | `0x4181E33C` |
| `XUITimeline_SetTimelines` | `0x4181E3A8` |
| `XUITimeline_Clear` | `0x4181E3D0` |
| `XUITimeline_Destruct` | `0x4181E434` |

**XUITimelineCtl** (2)

| Export | VA |
|---|---|
| `XUITimelineCtl_Construct` | `0x4181E438` |
| `XUITimelineCtl_Reset` | `0x4181E438` |

**XUITimelinePropPath** (4)

| Export | VA |
|---|---|
| `XUITimelinePropPath_Construct` | `0x4181E450` |
| `XUITimelinePropPath_Destruct` | `0x4181E464` |
| `XUITimelinePropPath_Clear` | `0x4181E47C` |
| `XUITimelinePropPath_CopyTo` | `0x4181E4A8` |

**XuiAddCapture** (1)

| Export | VA |
|---|---|
| `XuiAddCapture` | `0x4181F1B0` |

**XuiAddTexture** (1)

| Export | VA |
|---|---|
| `XuiAddTexture` | `0x4180EE80` |

**XuiAlloc** (1)

| Export | VA |
|---|---|
| `XuiAlloc` | `0x4181F358` |

**XuiAnimAddTimeli** (1)

| Export | VA |
|---|---|
| `XuiAnimAddTimeline` | `0x4181E2A0` |

**XuiAnimRemoveTim** (1)

| Export | VA |
|---|---|
| `XuiAnimRemoveTimeline` | `0x4181DA40` |

**XuiAnimRun** (1)

| Export | VA |
|---|---|
| `XuiAnimRun` | `0x4181E2D8` |

**XuiApplyLocale** (1)

| Export | VA |
|---|---|
| `XuiApplyLocale` | `0x4182B9E0` |

**XuiAreSoundsEnab** (1)

| Export | VA |
|---|---|
| `XuiAreSoundsEnabled` | `0x4182DEEC` |

**XuiAttachTexture** (1)

| Export | VA |
|---|---|
| `XuiAttachTextureBrush` | `0x41812250` |

**XuiBroadcastMess** (1)

| Export | VA |
|---|---|
| `XuiBroadcastMessage` | `0x41823428` |

**XuiBrushAsyncCom** (1)

| Export | VA |
|---|---|
| `XuiBrushAsyncComplete` | `0x4180ED2C` |

**XuiBrushAsyncUpd** (1)

| Export | VA |
|---|---|
| `XuiBrushAsyncUpdate` | `0x4180ED08` |

**XuiBrushGetDims** (1)

| Export | VA |
|---|---|
| `XuiBrushGetDims` | `0x4180ECBC` |

**XuiBrushGetTextu** (1)

| Export | VA |
|---|---|
| `XuiBrushGetTexture` | `0x4180ED80` |

**XuiBrushIsLoaded** (1)

| Export | VA |
|---|---|
| `XuiBrushIsLoaded` | `0x4180ECE8` |

**XuiBrushSetXForm** (1)

| Export | VA |
|---|---|
| `XuiBrushSetXForm` | `0x4180EC6C` |

**XuiBubbleMessage** (1)

| Export | VA |
|---|---|
| `XuiBubbleMessage` | `0x418236B8` |

**XuiCanvasGetVani** (1)

| Export | VA |
|---|---|
| `XuiCanvasGetVanishingPoint` | `0x41832550` |

**XuiCanvasSetVani** (1)

| Export | VA |
|---|---|
| `XuiCanvasSetVanishingPoint` | `0x418324D4` |

**XuiClassDerivesF** (1)

| Export | VA |
|---|---|
| `XuiClassDerivesFrom` | `0x41820F88` |

**XuiClassGetPropD** (1)

| Export | VA |
|---|---|
| `XuiClassGetPropDef` | `0x4181F484` |

**XuiClassGetPrope** (2)

| Export | VA |
|---|---|
| `XuiClassGetPropertyDef` | `0x4181F4D4` |
| `XuiClassGetPropertyDefById` | `0x4181F57C` |

**XuiCleanupResour** (1)

| Export | VA |
|---|---|
| `XuiCleanupResourcesAfterFrame` | `0x4180F390` |

**XuiCloseMessageB** (1)

| Export | VA |
|---|---|
| `XuiCloseMessageBox` | `0x4183D60C` |

**XuiContinueMouse** (1)

| Export | VA |
|---|---|
| `XuiContinueMouseMessageProcessing` | `0x4181E9E8` |

**XuiControlAttach** (1)

| Export | VA |
|---|---|
| `XuiControlAttachVisual` | `0x41831974` |

**XuiControlDestro** (1)

| Export | VA |
|---|---|
| `XuiControlDestroyVisual` | `0x41831950` |

**XuiControlFromPo** (1)

| Export | VA |
|---|---|
| `XuiControlFromPoint` | `0x4181E89C` |

**XuiControlGetIte** (1)

| Export | VA |
|---|---|
| `XuiControlGetItemAssociation` | `0x41831CD4` |

**XuiControlGetLin** (1)

| Export | VA |
|---|---|
| `XuiControlGetLink` | `0x418342D0` |

**XuiControlGetNav** (1)

| Export | VA |
|---|---|
| `XuiControlGetNavigation` | `0x41831D40` |

**XuiControlGetVis** (2)

| Export | VA |
|---|---|
| `XuiControlGetVisual` | `0x41831864` |
| `XuiControlGetVisualName` | `0x41831914` |

**XuiControlIsBack** (1)

| Export | VA |
|---|---|
| `XuiControlIsBackButton` | `0x4182F090` |

**XuiControlIsNavB** (1)

| Export | VA |
|---|---|
| `XuiControlIsNavButton` | `0x4182F06C` |

**XuiControlIsVisu** (1)

| Export | VA |
|---|---|
| `XuiControlIsVisualLocked` | `0x418319BC` |

**XuiControlLockVi** (1)

| Export | VA |
|---|---|
| `XuiControlLockVisual` | `0x41831994` |

**XuiControlNaviga** (1)

| Export | VA |
|---|---|
| `XuiControlNavigateTo` | `0x418342CC` |

**XuiControlPlayOp** (1)

| Export | VA |
|---|---|
| `XuiControlPlayOptionalVisual` | `0x41831BD8` |

**XuiControlPlaySt** (2)

| Export | VA |
|---|---|
| `XuiControlPlayStandardVisual` | `0x418319D8` |
| `XuiControlPlayStandardVisualOnFocusChanging` | `0x41831A24` |

**XuiControlPlayVi** (2)

| Export | VA |
|---|---|
| `XuiControlPlayVisualRangeEx` | `0x41831A78` |
| `XuiControlPlayVisualRange` | `0x41831B50` |

**XuiControlSetIte** (1)

| Export | VA |
|---|---|
| `XuiControlSetItemAssociation` | `0x41831CEC` |

**XuiControlSetVis** (1)

| Export | VA |
|---|---|
| `XuiControlSetVisualName` | `0x4183192C` |

**XuiControlWantsU** (1)

| Export | VA |
|---|---|
| `XuiControlWantsUnfocusedInput` | `0x41831D14` |

**XuiCopyString** (1)

| Export | VA |
|---|---|
| `XuiCopyString` | `0x4181F624` |

**XuiCreateEmptyTe** (2)

| Export | VA |
|---|---|
| `XuiCreateEmptyTextureBrush` | `0x418120E0` |
| `XuiCreateEmptyTextureBrushEx` | `0x41812188` |

**XuiCreateFont** (1)

| Export | VA |
|---|---|
| `XuiCreateFont` | `0x41816E90` |

**XuiCreateGradien** (1)

| Export | VA |
|---|---|
| `XuiCreateGradientTexture` | `0x4181231C` |

**XuiCreateLinearG** (1)

| Export | VA |
|---|---|
| `XuiCreateLinearGradientBrush` | `0x41812C6C` |

**XuiCreateObject** (1)

| Export | VA |
|---|---|
| `XuiCreateObject` | `0x41828E34` |

**XuiCreateObjectF** (1)

| Export | VA |
|---|---|
| `XuiCreateObjectFromData` | `0x41835944` |

**XuiCreateRadialG** (1)

| Export | VA |
|---|---|
| `XuiCreateRadialGradientBrush` | `0x41812D14` |

**XuiCreateSolidBr** (1)

| Export | VA |
|---|---|
| `XuiCreateSolidBrush` | `0x41811F1C` |

**XuiCreateSubtime** (1)

| Export | VA |
|---|---|
| `XuiCreateSubtimelineFromData` | `0x4183459C` |

**XuiCreateTexture** (3)

| Export | VA |
|---|---|
| `XuiCreateTextureCache` | `0x4180E9E4` |
| `XuiCreateTextureBrush` | `0x41811FA0` |
| `XuiCreateTextureBrushAsync` | `0x41812040` |

**XuiCreateTimelin** (1)

| Export | VA |
|---|---|
| `XuiCreateTimelineFromData` | `0x418345FC` |

**XuiDestroyBrush** (1)

| Export | VA |
|---|---|
| `XuiDestroyBrush` | `0x4180ED50` |

**XuiDestroyObject** (1)

| Export | VA |
|---|---|
| `XuiDestroyObject` | `0x41828E7C` |

**XuiDestroyTextur** (1)

| Export | VA |
|---|---|
| `XuiDestroyTextureCache` | `0x41811DE4` |

**XuiDestroyTypefa** (1)

| Export | VA |
|---|---|
| `XuiDestroyTypefaceList` | `0x418481D8` |

**XuiDrawShape** (1)

| Export | VA |
|---|---|
| `XuiDrawShape` | `0x41811F00` |

**XuiDrawText** (1)

| Export | VA |
|---|---|
| `XuiDrawText` | `0x41818EB4` |

**XuiDrawTextEx** (1)

| Export | VA |
|---|---|
| `XuiDrawTextEx` | `0x41818AA8` |

**XuiDynamicCast** (1)

| Export | VA |
|---|---|
| `XuiDynamicCast` | `0x418233A0` |

**XuiEditGetReadOn** (1)

| Export | VA |
|---|---|
| `XuiEditGetReadOnly` | `0x4183787C` |

**XuiEditGetTextFo** (1)

| Export | VA |
|---|---|
| `XuiEditGetTextFormatInfo` | `0x4183781C` |

**XuiEditGetTextLi** (1)

| Export | VA |
|---|---|
| `XuiEditGetTextLimit` | `0x418378BC` |

**XuiEditSetReadOn** (1)

| Export | VA |
|---|---|
| `XuiEditSetReadOnly` | `0x4183D354` |

**XuiEditSetTextFo** (1)

| Export | VA |
|---|---|
| `XuiEditSetTextFormatInfo` | `0x41842140` |

**XuiEditSetTextLi** (1)

| Export | VA |
|---|---|
| `XuiEditSetTextLimit` | `0x418449E4` |

**XuiElementAddChi** (1)

| Export | VA |
|---|---|
| `XuiElementAddChild` | `0x4182F3D4` |

**XuiElementBeginR** (1)

| Export | VA |
|---|---|
| `XuiElementBeginRender` | `0x41830764` |

**XuiElementCleanT** (1)

| Export | VA |
|---|---|
| `XuiElementCleanTree` | `0x4182F2E8` |

**XuiElementDisall** (1)

| Export | VA |
|---|---|
| `XuiElementDisallowRecursiveTimelineControl` | `0x418305A0` |

**XuiElementDiscar** (1)

| Export | VA |
|---|---|
| `XuiElementDiscardResources` | `0x41832178` |

**XuiElementEnable** (1)

| Export | VA |
|---|---|
| `XuiElementEnableInput` | `0x4182F328` |

**XuiElementEndRen** (1)

| Export | VA |
|---|---|
| `XuiElementEndRender` | `0x418307B0` |

**XuiElementFindNa** (1)

| Export | VA |
|---|---|
| `XuiElementFindNamedFrame` | `0x41830530` |

**XuiElementFromPo** (1)

| Export | VA |
|---|---|
| `XuiElementFromPoint` | `0x41832070` |

**XuiElementGetAnc** (1)

| Export | VA |
|---|---|
| `XuiElementGetAnchor` | `0x4182FF34` |

**XuiElementGetBle** (1)

| Export | VA |
|---|---|
| `XuiElementGetBlendMode` | `0x4182FFCC` |

**XuiElementGetBlu** (1)

| Export | VA |
|---|---|
| `XuiElementGetBlur` | `0x4182FAB4` |

**XuiElementGetBre** (1)

| Export | VA |
|---|---|
| `XuiElementGetBreadcrumbSource` | `0x41830104` |

**XuiElementGetChi** (1)

| Export | VA |
|---|---|
| `XuiElementGetChildById` | `0x4182FCE8` |

**XuiElementGetCol** (1)

| Export | VA |
|---|---|
| `XuiElementGetColorWriteFlags` | `0x41830068` |

**XuiElementGetDes** (1)

| Export | VA |
|---|---|
| `XuiElementGetDescendantById` | `0x4182FD58` |

**XuiElementGetEnt** (1)

| Export | VA |
|---|---|
| `XuiElementGetEnterTransIndex` | `0x4183018C` |

**XuiElementGetFir** (1)

| Export | VA |
|---|---|
| `XuiElementGetFirstChild` | `0x4182F4C0` |

**XuiElementGetFoc** (2)

| Export | VA |
|---|---|
| `XuiElementGetFocus` | `0x4182ED74` |
| `XuiElementGetFocusUser` | `0x4182EE40` |

**XuiElementGetFul** (2)

| Export | VA |
|---|---|
| `XuiElementGetFullXForm` | `0x4182F70C` |
| `XuiElementGetFullInvXForm` | `0x4182F764` |

**XuiElementGetHit** (1)

| Export | VA |
|---|---|
| `XuiElementGetHittable` | `0x4182FC44` |

**XuiElementGetId** (1)

| Export | VA |
|---|---|
| `XuiElementGetId` | `0x4182FC9C` |

**XuiElementGetLas** (1)

| Export | VA |
|---|---|
| `XuiElementGetLastChild` | `0x4182F528` |

**XuiElementGetLea** (1)

| Export | VA |
|---|---|
| `XuiElementGetLeaveTransIndex` | `0x41830214` |

**XuiElementGetNex** (1)

| Export | VA |
|---|---|
| `XuiElementGetNext` | `0x4182F590` |

**XuiElementGetOpa** (1)

| Export | VA |
|---|---|
| `XuiElementGetOpacity` | `0x4182FA18` |

**XuiElementGetPar** (1)

| Export | VA |
|---|---|
| `XuiElementGetParent` | `0x4182F458` |

**XuiElementGetPiv** (1)

| Export | VA |
|---|---|
| `XuiElementGetPivot` | `0x4182FBA8` |

**XuiElementGetPos** (1)

| Export | VA |
|---|---|
| `XuiElementGetPosition` | `0x4182F808` |

**XuiElementGetPre** (1)

| Export | VA |
|---|---|
| `XuiElementGetPrev` | `0x4182F5F8` |

**XuiElementGetRot** (1)

| Export | VA |
|---|---|
| `XuiElementGetRotation` | `0x4182F8B8` |

**XuiElementGetSca** (1)

| Export | VA |
|---|---|
| `XuiElementGetScale` | `0x4182F910` |

**XuiElementGetTim** (1)

| Export | VA |
|---|---|
| `XuiElementGetTimeline` | `0x4183029C` |

**XuiElementGetUse** (2)

| Export | VA |
|---|---|
| `XuiElementGetUserFocus` | `0x4182ED84` |
| `XuiElementGetUserData` | `0x4182FE84` |

**XuiElementGetXFo** (1)

| Export | VA |
|---|---|
| `XuiElementGetXForm` | `0x4182F6B4` |

**XuiElementHasFoc** (1)

| Export | VA |
|---|---|
| `XuiElementHasFocus` | `0x4182EDD4` |

**XuiElementInitFo** (1)

| Export | VA |
|---|---|
| `XuiElementInitFocus` | `0x41835C94` |

**XuiElementInitUs** (1)

| Export | VA |
|---|---|
| `XuiElementInitUserFocus` | `0x4183550C` |

**XuiElementInputE** (1)

| Export | VA |
|---|---|
| `XuiElementInputEnabled` | `0x4182F37C` |

**XuiElementInsert** (1)

| Export | VA |
|---|---|
| `XuiElementInsertChild` | `0x4182F660` |

**XuiElementIsDesc** (1)

| Export | VA |
|---|---|
| `XuiElementIsDescendant` | `0x4182FDD0` |

**XuiElementIsLayo** (1)

| Export | VA |
|---|---|
| `XuiElementIsLayoutLineBreak` | `0x4182EF40` |

**XuiElementIsShow** (1)

| Export | VA |
|---|---|
| `XuiElementIsShown` | `0x4182EEB0` |

**XuiElementIsSusp** (1)

| Export | VA |
|---|---|
| `XuiElementIsSuspended` | `0x418305F4` |

**XuiElementLayout** (1)

| Export | VA |
|---|---|
| `XuiElementLayoutTree` | `0x4182F170` |

**XuiElementPlayNa** (2)

| Export | VA |
|---|---|
| `XuiElementPlayNamedFrames` | `0x41830390` |
| `XuiElementPlayNamedFramesToEnd` | `0x41830490` |

**XuiElementPlayTi** (1)

| Export | VA |
|---|---|
| `XuiElementPlayTimeline` | `0x4183032C` |

**XuiElementPropVa** (2)

| Export | VA |
|---|---|
| `XuiElementPropValFromString` | `0x41821004` |
| `XuiElementPropValFromBinary` | `0x418211EC` |

**XuiElementRender** (1)

| Export | VA |
|---|---|
| `XuiElementRenderChildren` | `0x418307BC` |

**XuiElementSetBle** (1)

| Export | VA |
|---|---|
| `XuiElementSetBlendMode` | `0x4182FF88` |

**XuiElementSetBlu** (1)

| Export | VA |
|---|---|
| `XuiElementSetBlur` | `0x4182FB0C` |

**XuiElementSetBou** (1)

| Export | VA |
|---|---|
| `XuiElementSetBounds` | `0x4182F7BC` |

**XuiElementSetBre** (1)

| Export | VA |
|---|---|
| `XuiElementSetBreadcrumbSource` | `0x418300C0` |

**XuiElementSetCol** (1)

| Export | VA |
|---|---|
| `XuiElementSetColorWriteFlags` | `0x41830020` |

**XuiElementSetDir** (1)

| Export | VA |
|---|---|
| `XuiElementSetDirty` | `0x4182F228` |

**XuiElementSetEnt** (1)

| Export | VA |
|---|---|
| `XuiElementSetEnterTransIndex` | `0x41830148` |

**XuiElementSetFoc** (1)

| Export | VA |
|---|---|
| `XuiElementSetFocus` | `0x4182EDC8` |

**XuiElementSetHit** (1)

| Export | VA |
|---|---|
| `XuiElementSetHittable` | `0x4182FC00` |

**XuiElementSetLay** (2)

| Export | VA |
|---|---|
| `XuiElementSetLayoutLineBreak` | `0x4182EF88` |
| `XuiElementSetLayoutDirty` | `0x4182F1B0` |

**XuiElementSetLea** (1)

| Export | VA |
|---|---|
| `XuiElementSetLeaveTransIndex` | `0x418301D0` |

**XuiElementSetOpa** (1)

| Export | VA |
|---|---|
| `XuiElementSetOpacity` | `0x4182FA70` |

**XuiElementSetPiv** (1)

| Export | VA |
|---|---|
| `XuiElementSetPivot` | `0x4182FB50` |

**XuiElementSetPos** (1)

| Export | VA |
|---|---|
| `XuiElementSetPosition` | `0x4182F860` |

**XuiElementSetRot** (1)

| Export | VA |
|---|---|
| `XuiElementSetRotation` | `0x4182F9C0` |

**XuiElementSetSca** (1)

| Export | VA |
|---|---|
| `XuiElementSetScale` | `0x4182F968` |

**XuiElementSetSub** (1)

| Export | VA |
|---|---|
| `XuiElementSetSubtimeline` | `0x41832110` |

**XuiElementSetTim** (1)

| Export | VA |
|---|---|
| `XuiElementSetTimeline` | `0x41830258` |

**XuiElementSetUse** (2)

| Export | VA |
|---|---|
| `XuiElementSetUserData` | `0x4182FE44` |
| `XuiElementSetUserFocus` | `0x418342C4` |

**XuiElementStopTi** (1)

| Export | VA |
|---|---|
| `XuiElementStopTimeline` | `0x418302E8` |

**XuiElementSuspen** (1)

| Export | VA |
|---|---|
| `XuiElementSuspend` | `0x418322F0` |

**XuiElementTreeDe** (1)

| Export | VA |
|---|---|
| `XuiElementTreeDelayedInitialization` | `0x41832FBC` |

**XuiElementTreeGe** (1)

| Export | VA |
|---|---|
| `XuiElementTreeGetFocus` | `0x41831608` |

**XuiElementTreeHa** (1)

| Export | VA |
|---|---|
| `XuiElementTreeHasFocus` | `0x41831574` |

**XuiElementTreeIs** (2)

| Export | VA |
|---|---|
| `XuiElementTreeIsLayoutDirty` | `0x4182F1F0` |
| `XuiElementTreeIsDirty` | `0x4182F2AC` |

**XuiElementTreeSe** (1)

| Export | VA |
|---|---|
| `XuiElementTreeSetClassDirty` | `0x4182F268` |

**XuiElementUnlink** (1)

| Export | VA |
|---|---|
| `XuiElementUnlink` | `0x4182F418` |

**XuiElementWantFo** (1)

| Export | VA |
|---|---|
| `XuiElementWantFocus` | `0x418314C4` |

**XuiEnableSounds** (1)

| Export | VA |
|---|---|
| `XuiEnableSounds` | `0x4182DED4` |

**XuiEnumerateType** (1)

| Export | VA |
|---|---|
| `XuiEnumerateTypefaces` | `0x418482E0` |

**XuiFigureClose** (1)

| Export | VA |
|---|---|
| `XuiFigureClose` | `0x41832328` |

**XuiFigureDeleteP** (1)

| Export | VA |
|---|---|
| `XuiFigureDeletePoint` | `0x41832370` |

**XuiFigureGetFill** (1)

| Export | VA |
|---|---|
| `XuiFigureGetFillXForm` | `0x4183242C` |

**XuiFigureGetScal** (1)

| Export | VA |
|---|---|
| `XuiFigureGetScalingFactor` | `0x41832494` |

**XuiFigureGetShap** (1)

| Export | VA |
|---|---|
| `XuiFigureGetShape` | `0x41832F94` |

**XuiFigureIsClose** (1)

| Export | VA |
|---|---|
| `XuiFigureIsClosed` | `0x41832354` |

**XuiFigureSetFill** (1)

| Export | VA |
|---|---|
| `XuiFigureSetFill` | `0x41834534` |

**XuiFigureSetShap** (1)

| Export | VA |
|---|---|
| `XuiFigureSetShape` | `0x41832464` |

**XuiFigureSetStro** (1)

| Export | VA |
|---|---|
| `XuiFigureSetStroke` | `0x4183239C` |

**XuiFigureSetText** (1)

| Export | VA |
|---|---|
| `XuiFigureSetTexture` | `0x418323F0` |

**XuiFillRect** (1)

| Export | VA |
|---|---|
| `XuiFillRect` | `0x41811EE4` |

**XuiFindClass** (1)

| Export | VA |
|---|---|
| `XuiFindClass` | `0x41822A18` |

**XuiFontSetRender** (1)

| Export | VA |
|---|---|
| `XuiFontSetRenderer` | `0x41814564` |

**XuiFree** (1)

| Export | VA |
|---|---|
| `XuiFree` | `0x4181F3C0` |

**XuiFreeStringTab** (1)

| Export | VA |
|---|---|
| `XuiFreeStringTable` | `0x41832F64` |

**XuiFreeUnusedSou** (1)

| Export | VA |
|---|---|
| `XuiFreeUnusedSounds` | `0x4182EA98` |

**XuiFreeUnusedTex** (1)

| Export | VA |
|---|---|
| `XuiFreeUnusedTextures` | `0x4180EFE4` |

**XuiFreeVisuals** (1)

| Export | VA |
|---|---|
| `XuiFreeVisuals` | `0x4183644C` |

**XuiGetBaseClass** (1)

| Export | VA |
|---|---|
| `XuiGetBaseClass` | `0x4181F464` |

**XuiGetBaseObject** (1)

| Export | VA |
|---|---|
| `XuiGetBaseObject` | `0x41822AC0` |

**XuiGetCharAdvanc** (1)

| Export | VA |
|---|---|
| `XuiGetCharAdvance` | `0x41815FEC` |

**XuiGetCharMetric** (1)

| Export | VA |
|---|---|
| `XuiGetCharMetrics` | `0x418157E4` |

**XuiGetClass** (1)

| Export | VA |
|---|---|
| `XuiGetClass` | `0x4181F428` |

**XuiGetColorFacto** (1)

| Export | VA |
|---|---|
| `XuiGetColorFactor` | `0x4180EE60` |

**XuiGetDCStats** (1)

| Export | VA |
|---|---|
| `XuiGetDCStats` | `0x4180EDBC` |

**XuiGetDefaultLin** (1)

| Export | VA |
|---|---|
| `XuiGetDefaultLineBreakStyle` | `0x41814540` |

**XuiGetFontDescri** (1)

| Export | VA |
|---|---|
| `XuiGetFontDescriptor` | `0x41814230` |

**XuiGetFontHeight** (1)

| Export | VA |
|---|---|
| `XuiGetFontHeight` | `0x418141D0` |

**XuiGetFontMetric** (1)

| Export | VA |
|---|---|
| `XuiGetFontMetrics` | `0x41814358` |

**XuiGetFontPointS** (1)

| Export | VA |
|---|---|
| `XuiGetFontPointSize` | `0x41814298` |

**XuiGetFontStyle** (1)

| Export | VA |
|---|---|
| `XuiGetFontStyle` | `0x418142F8` |

**XuiGetInterruptT** (1)

| Export | VA |
|---|---|
| `XuiGetInterruptTransitionsDefault` | `0x4182F10C` |

**XuiGetLocale** (1)

| Export | VA |
|---|---|
| `XuiGetLocale` | `0x4181F69C` |

**XuiGetMemorySize** (1)

| Export | VA |
|---|---|
| `XuiGetMemorySize` | `0x4181F384` |

**XuiGetMouseHover** (1)

| Export | VA |
|---|---|
| `XuiGetMouseHover` | `0x4184D10C` |

**XuiGetObjectClas** (1)

| Export | VA |
|---|---|
| `XuiGetObjectClass` | `0x41822B4C` |

**XuiGetOuter** (1)

| Export | VA |
|---|---|
| `XuiGetOuter` | `0x418238D0` |

**XuiGetPressedCon** (1)

| Export | VA |
|---|---|
| `XuiGetPressedControl` | `0x4182F054` |

**XuiGetRuntimeHoo** (1)

| Export | VA |
|---|---|
| `XuiGetRuntimeHooks` | `0x4181F6AC` |

**XuiGetSleepInter** (1)

| Export | VA |
|---|---|
| `XuiGetSleepInterval` | `0x4181F6D4` |

**XuiGetSoundFacto** (1)

| Export | VA |
|---|---|
| `XuiGetSoundFactor` | `0x4182DEC4` |

**XuiGetTextBreaks** (1)

| Export | VA |
|---|---|
| `XuiGetTextBreaks` | `0x41818D8C` |

**XuiGetTextDropSh** (1)

| Export | VA |
|---|---|
| `XuiGetTextDropShadowColor` | `0x4180EE40` |

**XuiGetTextureInf** (1)

| Export | VA |
|---|---|
| `XuiGetTextureInfo` | `0x4180EF28` |

**XuiGetThreadGlob** (1)

| Export | VA |
|---|---|
| `XuiGetThreadGlobals` | `0x4181F6F4` |

**XuiGetTouchSetti** (1)

| Export | VA |
|---|---|
| `XuiGetTouchSettings` | `0x41848E10` |

**XuiGetVersion** (1)

| Export | VA |
|---|---|
| `XuiGetVersion` | `0x4181F420` |

**XuiHandleIsValid** (1)

| Export | VA |
|---|---|
| `XuiHandleIsValid` | `0x41822B74` |

**XuiHasCapture** (1)

| Export | VA |
|---|---|
| `XuiHasCapture` | `0x4181EC9C` |

**XuiHasTimer** (1)

| Export | VA |
|---|---|
| `XuiHasTimer` | `0x4184C9C0` |

**XuiHtmlControlGe** (1)

| Export | VA |
|---|---|
| `XuiHtmlControlGetVScrollInfo` | `0x41804E48` |

**XuiHtmlControlSe** (1)

| Export | VA |
|---|---|
| `XuiHtmlControlSetLineHeight` | `0x41804E04` |

**XuiHtmlControlVS** (1)

| Export | VA |
|---|---|
| `XuiHtmlControlVScrollBy` | `0x41804DD0` |

**XuiHtmlRegister** (1)

| Export | VA |
|---|---|
| `XuiHtmlRegister` | `0x41809210` |

**XuiHtmlUnregiste** (1)

| Export | VA |
|---|---|
| `XuiHtmlUnregister` | `0x41804D84` |

**XuiImageElementG** (3)

| Export | VA |
|---|---|
| `XuiImageElementGetContentInfo` | `0x418306C0` |
| `XuiImageElementGetImagePath` | `0x41832954` |
| `XuiImageElementGetLastError` | `0x41832988` |

**XuiImageElementM** (1)

| Export | VA |
|---|---|
| `XuiImageElementMeasureImage` | `0x418329A4` |

**XuiImageElementS** (1)

| Export | VA |
|---|---|
| `XuiImageElementSetImagePath` | `0x41830688` |

**XuiInit** (1)

| Export | VA |
|---|---|
| `XuiInit` | `0x4182DB38` |

**XuiInitThreadGlo** (1)

| Export | VA |
|---|---|
| `XuiInitThreadGlobals` | `0x4184D10C` |

**XuiIsInstanceOf** (1)

| Export | VA |
|---|---|
| `XuiIsInstanceOf` | `0x41823164` |

**XuiIsTreeLocked** (1)

| Export | VA |
|---|---|
| `XuiIsTreeLocked` | `0x4182F14C` |

**XuiKeepResources** (1)

| Export | VA |
|---|---|
| `XuiKeepResourcesAcrossFrame` | `0x4180F378` |

**XuiKillTimer** (1)

| Export | VA |
|---|---|
| `XuiKillTimer` | `0x4184C934` |

**XuiLoadFromBinar** (1)

| Export | VA |
|---|---|
| `XuiLoadFromBinary` | `0x4182BB08` |

**XuiLoadObjectDat** (1)

| Export | VA |
|---|---|
| `XuiLoadObjectDataFromBinary` | `0x4181F620` |

**XuiLoadStringTab** (2)

| Export | VA |
|---|---|
| `XuiLoadStringTableFromMemory` | `0x41832EC8` |
| `XuiLoadStringTableFromFile` | `0x418348F4` |

**XuiLoadVisualFro** (1)

| Export | VA |
|---|---|
| `XuiLoadVisualFromBinary` | `0x41836598` |

**XuiLockTree** (1)

| Export | VA |
|---|---|
| `XuiLockTree` | `0x4182F11C` |

**XuiLookupStringT** (2)

| Export | VA |
|---|---|
| `XuiLookupStringTableByIndex` | `0x4183070C` |
| `XuiLookupStringTable` | `0x41830730` |

**XuiLookupTexture** (1)

| Export | VA |
|---|---|
| `XuiLookupTexture` | `0x4180EEDC` |

**XuiMeasureText** (1)

| Export | VA |
|---|---|
| `XuiMeasureText` | `0x41818948` |

**XuiMemoryInit** (1)

| Export | VA |
|---|---|
| `XuiMemoryInit` | `0x4181F2C0` |

**XuiMessageBoxBut** (2)

| Export | VA |
|---|---|
| `XuiMessageBoxButtonUnregister` | `0x4184CD84` |
| `XuiMessageBoxButtonRegister` | `0x4184D080` |

**XuiMouseMessageF** (1)

| Export | VA |
|---|---|
| `XuiMouseMessageFromWinMsg` | `0x4181E698` |

**XuiMultilineEdit** (9)

| Export | VA |
|---|---|
| `XuiMultilineEditGetReadOnly` | `0x4184E014` |
| `XuiMultilineEditGetTextLimit` | `0x4184E04C` |
| `XuiMultilineEditGetSel` | `0x4184E070` |
| `XuiMultilineEditGetMultiline` | `0x4184E0C8` |
| `XuiMultilineEditSetReadOnly` | `0x4184F9F4` |
| `XuiMultilineEditSetSel` | `0x4184FA3C` |
| `XuiMultilineEditSetMultiline` | `0x4184FA7C` |
| `XuiMultilineEditSetTextLimit` | `0x41851240` |
| `XuiMultilineEditSetPasswordChar` | `0x41851274` |

**XuiMuteSound** (1)

| Export | VA |
|---|---|
| `XuiMuteSound` | `0x4182DE64` |

**XuiNavButtonGetP** (1)

| Export | VA |
|---|---|
| `XuiNavButtonGetPressPath` | `0x41831D78` |

**XuiNavButtonGetS** (1)

| Export | VA |
|---|---|
| `XuiNavButtonGetStayVisible` | `0x41831DB8` |

**XuiNavButtonGetT** (1)

| Export | VA |
|---|---|
| `XuiNavButtonGetTransIndices` | `0x41831E0C` |

**XuiObjectFromHan** (1)

| Export | VA |
|---|---|
| `XuiObjectFromHandle` | `0x41822A30` |

**XuiObjectGetProp** (7)

| Export | VA |
|---|---|
| `XuiObjectGetPropDef` | `0x41822BCC` |
| `XuiObjectGetPropertyId` | `0x41822C3C` |
| `XuiObjectGetPropertyDefById` | `0x41822D34` |
| `XuiObjectGetPropertyDef` | `0x41822DEC` |
| `XuiObjectGetProperty` | `0x41822EA4` |
| `XuiObjectGetPropertyRef` | `0x4182307C` |
| `XuiObjectGetPropertyCount` | `0x41828F98` |

**XuiObjectSetProp** (1)

| Export | VA |
|---|---|
| `XuiObjectSetProperty` | `0x41822F90` |

**XuiPlayingInitia** (1)

| Export | VA |
|---|---|
| `XuiPlayingInitialFocus` | `0x4182ED58` |

**XuiPointInRect** (1)

| Export | VA |
|---|---|
| `XuiPointInRect` | `0x418378E8` |

**XuiProcessInput** (1)

| Export | VA |
|---|---|
| `XuiProcessInput` | `0x41831698` |

**XuiProcessMouseM** (1)

| Export | VA |
|---|---|
| `XuiProcessMouseMessage` | `0x4181F038` |

**XuiProcessMultiT** (1)

| Export | VA |
|---|---|
| `XuiProcessMultiTouchMessage` | `0x4181F100` |

**XuiProgressBarGe** (2)

| Export | VA |
|---|---|
| `XuiProgressBarGetRange` | `0x41832A80` |
| `XuiProgressBarGetValue` | `0x41832B4C` |

**XuiProgressBarSe** (2)

| Export | VA |
|---|---|
| `XuiProgressBarSetRange` | `0x41832A10` |
| `XuiProgressBarSetValue` | `0x41832AF8` |

**XuiRealloc** (1)

| Export | VA |
|---|---|
| `XuiRealloc` | `0x4181F3F4` |

**XuiRegisterClass** (1)

| Export | VA |
|---|---|
| `XuiRegisterClass` | `0x41828E28` |

**XuiRegisterResou** (1)

| Export | VA |
|---|---|
| `XuiRegisterResourceTransport` | `0x418544E8` |

**XuiRegisterTypef** (1)

| Export | VA |
|---|---|
| `XuiRegisterTypeface` | `0x41848700` |

**XuiRegisterZuneC** (1)

| Export | VA |
|---|---|
| `XuiRegisterZuneClasses` | `0x41861830` |

**XuiReleaseCaptur** (1)

| Export | VA |
|---|---|
| `XuiReleaseCapture` | `0x4181EF64` |

**XuiReleaseFont** (1)

| Export | VA |
|---|---|
| `XuiReleaseFont` | `0x41817008` |

**XuiReleaseTextur** (1)

| Export | VA |
|---|---|
| `XuiReleaseTexture` | `0x4180EF74` |

**XuiRenderAttachD** (1)

| Export | VA |
|---|---|
| `XuiRenderAttachDevice` | `0x4180E8A8` |

**XuiRenderBegin** (1)

| Export | VA |
|---|---|
| `XuiRenderBegin` | `0x418139A8` |

**XuiRenderBeginEx** (1)

| Export | VA |
|---|---|
| `XuiRenderBeginEx` | `0x41813814` |

**XuiRenderClear** (1)

| Export | VA |
|---|---|
| `XuiRenderClear` | `0x41811EA8` |

**XuiRenderCreateD** (1)

| Export | VA |
|---|---|
| `XuiRenderCreateDC` | `0x41812A88` |

**XuiRenderCreateS** (2)

| Export | VA |
|---|---|
| `XuiRenderCreateShape` | `0x41813830` |
| `XuiRenderCreateShapeScale` | `0x418138EC` |

**XuiRenderDCDevic** (1)

| Export | VA |
|---|---|
| `XuiRenderDCDeviceChanged` | `0x41811E54` |

**XuiRenderDestroy** (2)

| Export | VA |
|---|---|
| `XuiRenderDestroyDC` | `0x41811E7C` |
| `XuiRenderDestroyShape` | `0x418122F0` |

**XuiRenderEnd** (1)

| Export | VA |
|---|---|
| `XuiRenderEnd` | `0x4180EA44` |

**XuiRenderGetBack** (1)

| Export | VA |
|---|---|
| `XuiRenderGetBackBufferSize` | `0x4180EA60` |

**XuiRenderGetDevi** (1)

| Export | VA |
|---|---|
| `XuiRenderGetDevice` | `0x4180EB00` |

**XuiRenderGetInvV** (1)

| Export | VA |
|---|---|
| `XuiRenderGetInvViewTransform` | `0x4180EC04` |

**XuiRenderGetTarg** (1)

| Export | VA |
|---|---|
| `XuiRenderGetTarget` | `0x4180EAE4` |

**XuiRenderGetTran** (1)

| Export | VA |
|---|---|
| `XuiRenderGetTransform` | `0x4180EBA4` |

**XuiRenderGetView** (1)

| Export | VA |
|---|---|
| `XuiRenderGetViewTransform` | `0x4180EBD4` |

**XuiRenderGetXuiD** (1)

| Export | VA |
|---|---|
| `XuiRenderGetXuiDevice` | `0x4180EB64` |

**XuiRenderInit** (1)

| Export | VA |
|---|---|
| `XuiRenderInit` | `0x41811B18` |

**XuiRenderInitEx** (1)

| Export | VA |
|---|---|
| `XuiRenderInitEx` | `0x41811C68` |

**XuiRenderInitExc** (1)

| Export | VA |
|---|---|
| `XuiRenderInitExclusive` | `0x41811B5C` |

**XuiRenderInitSha** (1)

| Export | VA |
|---|---|
| `XuiRenderInitShared` | `0x41811B64` |

**XuiRenderPresent** (1)

| Export | VA |
|---|---|
| `XuiRenderPresent` | `0x4180EAC8` |

**XuiRenderResetDe** (1)

| Export | VA |
|---|---|
| `XuiRenderResetDevice` | `0x4180EB48` |

**XuiRenderResolut** (1)

| Export | VA |
|---|---|
| `XuiRenderResolutionChanged` | `0x4180E9B4` |

**XuiRenderRestore** (1)

| Export | VA |
|---|---|
| `XuiRenderRestoreState` | `0x41812B28` |

**XuiRenderSetTarg** (2)

| Export | VA |
|---|---|
| `XuiRenderSetTarget` | `0x41812B48` |
| `XuiRenderSetTargetBrush` | `0x41812B64` |

**XuiRenderSetText** (1)

| Export | VA |
|---|---|
| `XuiRenderSetTextureLoader` | `0x4180EA2C` |

**XuiRenderSetTran** (1)

| Export | VA |
|---|---|
| `XuiRenderSetTransform` | `0x41812C18` |

**XuiRenderSetView** (1)

| Export | VA |
|---|---|
| `XuiRenderSetViewTransform` | `0x41812C50` |

**XuiRenderUninit** (1)

| Export | VA |
|---|---|
| `XuiRenderUninit` | `0x41811E04` |

**XuiResourceClose** (1)

| Export | VA |
|---|---|
| `XuiResourceClose` | `0x41853430` |

**XuiResourceCompo** (1)

| Export | VA |
|---|---|
| `XuiResourceComposeLocator` | `0x418535C4` |

**XuiResourceGetBu** (1)

| Export | VA |
|---|---|
| `XuiResourceGetBuffer` | `0x418532E8` |

**XuiResourceGetCu** (1)

| Export | VA |
|---|---|
| `XuiResourceGetCurrentPosition` | `0x418533C8` |

**XuiResourceGetPa** (2)

| Export | VA |
|---|---|
| `XuiResourceGetPackageEntryCount` | `0x41853808` |
| `XuiResourceGetPackageEntryInfo` | `0x41853820` |

**XuiResourceGetTo** (1)

| Export | VA |
|---|---|
| `XuiResourceGetTotalSize` | `0x418533A8` |

**XuiResourceLoadA** (2)

| Export | VA |
|---|---|
| `XuiResourceLoadAll` | `0x41853FA0` |
| `XuiResourceLoadAllNoLoc` | `0x41853FF4` |

**XuiResourceLocat** (2)

| Export | VA |
|---|---|
| `XuiResourceLocatorIsAbsolute` | `0x41853550` |
| `XuiResourceLocatorIsFile` | `0x4185357C` |

**XuiResourceOpen** (1)

| Export | VA |
|---|---|
| `XuiResourceOpen` | `0x41853D40` |

**XuiResourceOpenN** (1)

| Export | VA |
|---|---|
| `XuiResourceOpenNoLoc` | `0x41853EE8` |

**XuiResourceOpenP** (1)

| Export | VA |
|---|---|
| `XuiResourceOpenPackage` | `0x4185429C` |

**XuiResourcePacka** (1)

| Export | VA |
|---|---|
| `XuiResourcePackageExpiration` | `0x418537D4` |

**XuiResourceRead** (1)

| Export | VA |
|---|---|
| `XuiResourceRead` | `0x41853340` |

**XuiResourceRelea** (2)

| Export | VA |
|---|---|
| `XuiResourceReleaseBuffer` | `0x4185331C` |
| `XuiResourceReleasePackage` | `0x418537D8` |

**XuiResourceSeek** (1)

| Export | VA |
|---|---|
| `XuiResourceSeek` | `0x4185340C` |

**XuiSceneCreate** (1)

| Export | VA |
|---|---|
| `XuiSceneCreate` | `0x418358D0` |

**XuiSceneCreateEx** (1)

| Export | VA |
|---|---|
| `XuiSceneCreateEx` | `0x41835604` |

**XuiSceneGetTrans** (1)

| Export | VA |
|---|---|
| `XuiSceneGetTransIndex` | `0x41832604` |

**XuiSceneInterrup** (1)

| Export | VA |
|---|---|
| `XuiSceneInterruptTransitions` | `0x41831F3C` |

**XuiSceneIsInTran** (1)

| Export | VA |
|---|---|
| `XuiSceneIsInTransition` | `0x41831F1C` |

**XuiSceneNavigate** (4)

| Export | VA |
|---|---|
| `XuiSceneNavigateFirst` | `0x41835CA0` |
| `XuiSceneNavigateForward` | `0x41835DCC` |
| `XuiSceneNavigateBack` | `0x41835F98` |
| `XuiSceneNavigateBackToFirst` | `0x418361A8` |

**XuiScenePlayBack** (2)

| Export | VA |
|---|---|
| `XuiScenePlayBackFromTransition` | `0x41835AA4` |
| `XuiScenePlayBackToTransition` | `0x41835AD8` |

**XuiScenePlayFrom** (1)

| Export | VA |
|---|---|
| `XuiScenePlayFromTransition` | `0x41835B40` |

**XuiScenePlayToTr** (1)

| Export | VA |
|---|---|
| `XuiScenePlayToTransition` | `0x41835B0C` |

**XuiSceneSetTrans** (1)

| Export | VA |
|---|---|
| `XuiSceneSetTransIndex` | `0x418325CC` |

**XuiScrollEndGetD** (1)

| Export | VA |
|---|---|
| `XuiScrollEndGetDirection` | `0x41832010` |

**XuiSelectBrush** (1)

| Export | VA |
|---|---|
| `XuiSelectBrush` | `0x41811EC4` |

**XuiSelectBrushEx** (1)

| Export | VA |
|---|---|
| `XuiSelectBrushEx` | `0x4180EC34` |

**XuiSelectFont** (1)

| Export | VA |
|---|---|
| `XuiSelectFont` | `0x4180EC50` |

**XuiSendMessage** (1)

| Export | VA |
|---|---|
| `XuiSendMessage` | `0x41823634` |

**XuiSendMouseMess** (1)

| Export | VA |
|---|---|
| `XuiSendMouseMessage` | `0x4181E914` |

**XuiSetBlendMode** (1)

| Export | VA |
|---|---|
| `XuiSetBlendMode` | `0x41812418` |

**XuiSetCapture** (1)

| Export | VA |
|---|---|
| `XuiSetCapture` | `0x4181F25C` |

**XuiSetColorFacto** (1)

| Export | VA |
|---|---|
| `XuiSetColorFactor` | `0x418123FC` |

**XuiSetColorWrite** (1)

| Export | VA |
|---|---|
| `XuiSetColorWriteFlags` | `0x41812450` |

**XuiSetDefaultLin** (1)

| Export | VA |
|---|---|
| `XuiSetDefaultLineBreakStyle` | `0x418144E0` |

**XuiSetInputFilte** (1)

| Export | VA |
|---|---|
| `XuiSetInputFilter` | `0x4182F03C` |

**XuiSetInterruptT** (1)

| Export | VA |
|---|---|
| `XuiSetInterruptTransitionsDefault` | `0x4182F0F4` |

**XuiSetLocale** (1)

| Export | VA |
|---|---|
| `XuiSetLocale` | `0x41820FFC` |

**XuiSetLocaleEx** (1)

| Export | VA |
|---|---|
| `XuiSetLocaleEx` | `0x41820FB0` |

**XuiSetMessageFil** (1)

| Export | VA |
|---|---|
| `XuiSetMessageFilter` | `0x4184D10C` |

**XuiSetRuntimeHoo** (1)

| Export | VA |
|---|---|
| `XuiSetRuntimeHooks` | `0x4181F6BC` |

**XuiSetSoundFacto** (1)

| Export | VA |
|---|---|
| `XuiSetSoundFactor` | `0x4182DE9C` |

**XuiSetTextDropSh** (1)

| Export | VA |
|---|---|
| `XuiSetTextDropShadowColor` | `0x4180EE28` |

**XuiSetTextureMem** (1)

| Export | VA |
|---|---|
| `XuiSetTextureMemoryLimit` | `0x4180F04C` |

**XuiSetThreadGlob** (1)

| Export | VA |
|---|---|
| `XuiSetThreadGlobals` | `0x4184D10C` |

**XuiSetTimer** (1)

| Export | VA |
|---|---|
| `XuiSetTimer` | `0x4184C884` |

**XuiSetTouchSetti** (1)

| Export | VA |
|---|---|
| `XuiSetTouchSettings` | `0x41848DD0` |

**XuiSetWireFrame** (1)

| Export | VA |
|---|---|
| `XuiSetWireFrame` | `0x4180EDA4` |

**XuiShapeGetVerti** (1)

| Export | VA |
|---|---|
| `XuiShapeGetVertices` | `0x4180F088` |

**XuiShowMessageBo** (2)

| Export | VA |
|---|---|
| `XuiShowMessageBoxEx` | `0x4183D388` |
| `XuiShowMessageBox` | `0x4183D5C0` |

**XuiSliderGetAcce** (1)

| Export | VA |
|---|---|
| `XuiSliderGetAccel` | `0x41832E50` |

**XuiSliderGetRang** (1)

| Export | VA |
|---|---|
| `XuiSliderGetRange` | `0x41832C10` |

**XuiSliderGetStep** (1)

| Export | VA |
|---|---|
| `XuiSliderGetStep` | `0x41832D98` |

**XuiSliderGetValu** (1)

| Export | VA |
|---|---|
| `XuiSliderGetValue` | `0x41832CE0` |

**XuiSliderSetAcce** (1)

| Export | VA |
|---|---|
| `XuiSliderSetAccel` | `0x41832DFC` |

**XuiSliderSetRang** (1)

| Export | VA |
|---|---|
| `XuiSliderSetRange` | `0x41832BA0` |

**XuiSliderSetStep** (1)

| Export | VA |
|---|---|
| `XuiSliderSetStep` | `0x41832D44` |

**XuiSliderSetValu** (1)

| Export | VA |
|---|---|
| `XuiSliderSetValue` | `0x41832C88` |

**XuiSoundGetVolum** (1)

| Export | VA |
|---|---|
| `XuiSoundGetVolume` | `0x4182E29C` |

**XuiSoundIsDirty** (1)

| Export | VA |
|---|---|
| `XuiSoundIsDirty` | `0x4182DF4C` |

**XuiSoundIsInVisu** (1)

| Export | VA |
|---|---|
| `XuiSoundIsInVisual` | `0x4182DFAC` |

**XuiSoundIsMuted** (1)

| Export | VA |
|---|---|
| `XuiSoundIsMuted` | `0x4182E258` |

**XuiSoundMute** (1)

| Export | VA |
|---|---|
| `XuiSoundMute` | `0x4182E218` |

**XuiSoundSetDirty** (1)

| Export | VA |
|---|---|
| `XuiSoundSetDirty` | `0x4182DEFC` |

**XuiSoundSetFile** (1)

| Export | VA |
|---|---|
| `XuiSoundSetFile` | `0x41854FA8` |

**XuiSoundSetVolum** (1)

| Export | VA |
|---|---|
| `XuiSoundSetVolume` | `0x4182E2F0` |

**XuiSoundXAudioRe** (1)

| Export | VA |
|---|---|
| `XuiSoundXAudioRegister` | `0x41855504` |

**XuiSoundXAudioUn** (1)

| Export | VA |
|---|---|
| `XuiSoundXAudioUnregister` | `0x41854F48` |

**XuiStrokeShape** (1)

| Export | VA |
|---|---|
| `XuiStrokeShape` | `0x41812A6C` |

**XuiSubstituteGly** (1)

| Export | VA |
|---|---|
| `XuiSubstituteGlyph` | `0x4185F454` |

**XuiTextElementGe** (5)

| Export | VA |
|---|---|
| `XuiTextElementGetText` | `0x418326E0` |
| `XuiTextElementGetTextScale` | `0x4183276C` |
| `XuiTextElementGetFont` | `0x418327A0` |
| `XuiTextElementGetStyle` | `0x41832924` |
| `XuiTextElementGetLineSpacing` | `0x4183293C` |

**XuiTextElementMe** (1)

| Export | VA |
|---|---|
| `XuiTextElementMeasureText` | `0x4183270C` |

**XuiTextElementSe** (4)

| Export | VA |
|---|---|
| `XuiTextElementSetText` | `0x4183268C` |
| `XuiTextElementSetTypeface` | `0x418327F0` |
| `XuiTextElementSetPointSize` | `0x4183286C` |
| `XuiTextElementSetStyle` | `0x418328E4` |

**XuiTimersRun** (1)

| Export | VA |
|---|---|
| `XuiTimersRun` | `0x4184CA84` |

**XuiTouchEnableAx** (1)

| Export | VA |
|---|---|
| `XuiTouchEnableAxisHysteresis` | `0x418331CC` |

**XuiTouchEnableHo** (1)

| Export | VA |
|---|---|
| `XuiTouchEnableHorizontalAxis` | `0x4183317C` |

**XuiTouchEnableVe** (1)

| Export | VA |
|---|---|
| `XuiTouchEnableVerticalAxis` | `0x418331A4` |

**XuiTouchGetCanva** (1)

| Export | VA |
|---|---|
| `XuiTouchGetCanvasSize` | `0x418330BC` |

**XuiTouchGetPosit** (2)

| Export | VA |
|---|---|
| `XuiTouchGetPositionOffset` | `0x4183301C` |
| `XuiTouchGetPositionTarget` | `0x4183306C` |

**XuiTouchGetSetti** (1)

| Export | VA |
|---|---|
| `XuiTouchGetSettings` | `0x4183310C` |

**XuiTouchGetState** (1)

| Export | VA |
|---|---|
| `XuiTouchGetState` | `0x41833154` |

**XuiTouchSetCanva** (1)

| Export | VA |
|---|---|
| `XuiTouchSetCanvasSize` | `0x41833094` |

**XuiTouchSetPosit** (2)

| Export | VA |
|---|---|
| `XuiTouchSetPositionOffset` | `0x41832FF4` |
| `XuiTouchSetPositionTarget` | `0x41833044` |

**XuiTouchSetSetti** (1)

| Export | VA |
|---|---|
| `XuiTouchSetSettings` | `0x418330E4` |

**XuiTouchSnapToTa** (1)

| Export | VA |
|---|---|
| `XuiTouchSnapToTarget` | `0x41833134` |

**XuiUninit** (1)

| Export | VA |
|---|---|
| `XuiUninit` | `0x4182CF58` |

**XuiUninitThreadG** (1)

| Export | VA |
|---|---|
| `XuiUninitThreadGlobals` | `0x4184D10C` |

**XuiUnregisterCla** (1)

| Export | VA |
|---|---|
| `XuiUnregisterClass` | `0x4182B924` |

**XuiUnregisterRes** (1)

| Export | VA |
|---|---|
| `XuiUnregisterResourceTransport` | `0x41854434` |

**XuiUnregisterTyp** (1)

| Export | VA |
|---|---|
| `XuiUnregisterTypeface` | `0x4184864C` |

**XuiUnregisterZun** (1)

| Export | VA |
|---|---|
| `XuiUnregisterZuneClasses` | `0x41861868` |

**XuiVisualCreateI** (1)

| Export | VA |
|---|---|
| `XuiVisualCreateInstance` | `0x41835A9C` |

**XuiVisualFind** (1)

| Export | VA |
|---|---|
| `XuiVisualFind` | `0x418348D8` |

**XuiVisualGetBase** (1)

| Export | VA |
|---|---|
| `XuiVisualGetBasePath` | `0x418324BC` |

**XuiVisualGetCoun** (1)

| Export | VA |
|---|---|
| `XuiVisualGetCount` | `0x418324CC` |

**XuiVisualGetId** (1)

| Export | VA |
|---|---|
| `XuiVisualGetId` | `0x418324D0` |

**XuiVisualRegiste** (1)

| Export | VA |
|---|---|
| `XuiVisualRegister` | `0x41836390` |

**XuiVisualSetBase** (1)

| Export | VA |
|---|---|
| `XuiVisualSetBasePath` | `0x418358F8` |

**XuiVisualUnregis** (1)

| Export | VA |
|---|---|
| `XuiVisualUnregister` | `0x418361F4` |

## `zdksystem.dll` — 359 exports

| Export | VA |
|---|---|
| `ZDKAudio_Initialize` | `0x4197487C` |
| `ZDKAudio_Shutdown` | `0x419748C0` |
| `ZDKAudio_SetMasterVolume` | `0x419748E8` |
| `ZDKAudio_CreateVoice` | `0x41974904` |
| `ZDKAudio_DestroyVoice` | `0x41974978` |
| `ZDKAudio_SubmitPacket` | `0x41974994` |
| `ZDKAudio_Play` | `0x419749B4` |
| `ZDKAudio_Pause` | `0x419749D0` |
| `ZDKAudio_Stop` | `0x419749EC` |
| `ZDKAudio_SetVolume` | `0x41974A08` |
| `ZDKAudio_GetVolume` | `0x41974A24` |
| `ZDKAudio_SetPitch` | `0x41974A44` |
| `ZDKAudio_GetPitch` | `0x41974A60` |
| `ZDKAudio_SetPan` | `0x41974A80` |
| `ZDKAudio_GetPan` | `0x41974A9C` |
| `ZDKAudio_GetVoiceState` | `0x41974ABC` |
| `ZDKCloud_Connect` | `0x419769D0` |
| `ZDKCloud_Disconnect` | `0x419769E0` |
| `ZDKCloud_ShowConnectivityWizard` | `0x419769F0` |
| `ZDKCloud_IsConnected` | `0x419769F4` |
| `ZDKCloud_CreateJob` | `0x41976A10` |
| `ZDKCloud_GetTaskExecutionState` | `0x41976B00` |
| `ZDKCloud_CancelObjectAsync` | `0x41976BBC` |
| `ZDKCloud_CloseObject` | `0x41976BDC` |
| `ZDKCloud_GetNearbyNetworks` | `0x41976BFC` |
| `ZDKCloud_GetConnectionState` | `0x41976D20` |
| `ZDKCloud_CreateSemaphore` | `0x41976D48` |
| `ZDKCloud_WaitSemaphore` | `0x41976DE0` |
| `ZDKCloud_CloseSemaphore` | `0x41976E34` |
| `ZDKCloud_ScheduleJob` | `0x41976E4C` |
| `ZDKCloud_CreateWebRequestTask` | `0x41976EC4` |
| `ZDKCloud_GetTaskResults` | `0x419770A8` |
| `ZDKCloud_CreateWebDownloadTask` | `0x419771A4` |
| `ZDKContent_Flush` | `0x41977520` |
| `ZDKContent_Unmount` | `0x4197753C` |
| `ZDKContent_UnmountAll` | `0x41977568` |
| `ZDKContent_FindClose` | `0x4197756C` |
| `ZDKContent_Create` | `0x419779D0` |
| `ZDKContent_SetMetaData` | `0x41977BD4` |
| `ZDKContent_SetThumbnail` | `0x41977E44` |
| `ZDKContent_Delete` | `0x41978088` |
| `ZDKContent_Mount` | `0x419780D8` |
| `ZDKContent_FindFirst` | `0x419787A4` |
| `ZDKContent_FindNext` | `0x419788FC` |
| `ZuneExtensions_RegisterInteropModule` | `0x41978974` |
| `ZuneExtensions_UnregisterInteropModule` | `0x41978A18` |
| `ZuneExtensions_BeginShutdown` | `0x41978A1C` |
| `ZDKDisplay_Initialize` | `0x41978A3C` |
| `ZDKDisplay_GetInfo` | `0x41978A5C` |
| `ZDKDisplay_SetTransform` | `0x41978A60` |
| `ZDKDisplay_BeginScene` | `0x41978A94` |
| `ZDKDisplay_EndScene` | `0x41978ABC` |
| `ZDKDisplay_Present` | `0x41978AE4` |
| `ZDKDisplay_Clear` | `0x41978B0C` |
| `ZDKDisplay_GetTextureInfo` | `0x41978B3C` |
| `ZDKDisplay_SetRenderTarget` | `0x41978BD0` |
| `ZDKDisplay_SetTexture` | `0x41978C00` |
| `ZDKDisplay_SetClipRect` | `0x41978C30` |
| `ZDKDisplay_TextureLockRect` | `0x41978C60` |
| `ZDKDisplay_TextureUnlockRect` | `0x41978D00` |
| `ZDKDisplay_SetBlendMode` | `0x41978D40` |
| `ZDKDisplay_DrawSprites` | `0x41978D9C` |
| `ZDKDisplay_SetTextureData` | `0x41978FD8` |
| `ZDKDisplay_GetTextureData` | `0x419790EC` |
| `ZDKDisplay_ResolveBackBuffer` | `0x41979200` |
| `ZDKDisplay_SetTextureFilter` | `0x419793E8` |
| `ZDKDisplay_FreeTexture` | `0x41979500` |
| `ZDKDisplay_Cleanup` | `0x419795D8` |
| `ZDKDisplay_CreateTexture` | `0x4197966C` |
| `ZDKSystem_OpenHash` | `0x41979730` |
| `ZDKSystem_UpdateHash` | `0x419797C4` |
| `ZDKSystem_CloseHash` | `0x41979820` |
| `ZDKImage_CreateImageFromFile` | `0x419798C8` |
| `ZDKImage_CreateImageFromBuffer` | `0x419799DC` |
| `ZDKImage_GetImageSize` | `0x41979AD8` |
| `ZDKImage_GetImageData` | `0x41979B88` |
| `ZDKImage_ReleaseImage` | `0x41979DCC` |
| `ZDKInput_EnableInputMessages` | `0x41979DEC` |
| `ZDKInput_GetNextInputMessage` | `0x41979E04` |
| `ZDKInput_GetState` | `0x41979E28` |
| `ZDKMedia_Library_GetSongs` | `0x4197A768` |
| `ZDKMedia_Library_GetArtists` | `0x4197A774` |
| `ZDKMedia_Library_GetAlbums` | `0x4197A780` |
| `ZDKMedia_Library_GetGenres` | `0x4197A78C` |
| `ZDKMedia_Library_GetPlaylists` | `0x4197A798` |
| `ZDKMedia_Library_GetPictures` | `0x4197A7A4` |
| `ZDKMedia_Library_GetRootPictureAlbum` | `0x4197A7B0` |
| `ZDKMedia_Queue_GetSongCount` | `0x4197A7CC` |
| `ZDKMedia_Queue_GetSongAtIndex` | `0x4197A810` |
| `ZDKMedia_Queue_GetActiveSongIndex` | `0x4197A85C` |
| `ZDKMedia_Queue_MoveTo` | `0x4197A8A0` |
| `ZDKMedia_Queue_MoveNext` | `0x4197A8C8` |
| `ZDKMedia_Queue_MovePrev` | `0x4197A8E8` |
| `ZDKMedia_Queue_PlaySong` | `0x4197A908` |
| `ZDKMedia_Queue_PlaySongList` | `0x4197A95C` |
| `ZDKMedia_Queue_PlaySongFromURL` | `0x4197A998` |
| `ZDKMedia_Queue_SetShuffle` | `0x4197AA64` |
| `ZDKMedia_Queue_GetShuffle` | `0x4197AA8C` |
| `ZDKMedia_Queue_SetRepeat` | `0x4197AAE0` |
| `ZDKMedia_Queue_GetRepeat` | `0x4197AB08` |
| `ZDKMedia_Queue_EnableViz` | `0x4197AB5C` |
| `ZDKMedia_Queue_GetVizData` | `0x4197AB5C` |
| `ZDKMedia_Queue_IsVizEnabled` | `0x4197AB5C` |
| `ZDKMedia_Queue_SetVolume` | `0x4197AB68` |
| `ZDKMedia_Queue_GetVolume` | `0x4197AC38` |
| `ZDKMedia_Queue_Mute` | `0x4197ACE4` |
| `ZDKMedia_Queue_IsMuted` | `0x4197AD0C` |
| `ZDKMedia_Queue_SetPlayState` | `0x4197AD30` |
| `ZDKMedia_Queue_GetPlayState` | `0x4197AD90` |
| `ZDKMedia_Queue_SetPlayPosition` | `0x4197AE44` |
| `ZDKMedia_Queue_GetPlayPosition` | `0x4197AE5C` |
| `ZDKMedia_Item_GetName` | `0x4197AEC8` |
| `ZDKMedia_Item_GetFilePath` | `0x4197AF20` |
| `ZDKMedia_Item_GetMediaId` | `0x4197AF78` |
| `ZDKMedia_List_GetType` | `0x4197AFD8` |
| `ZDKMedia_List_GetItemCount` | `0x4197B104` |
| `ZDKMedia_List_GetItemAtIndex` | `0x4197B158` |
| `ZDKMedia_List_Release` | `0x4197B1AC` |
| `ZDKMedia_Album_GetArtist` | `0x4197B1C8` |
| `ZDKMedia_Song_GetArtist` | `0x4197B1C8` |
| `ZDKMedia_Song_GetAlbum` | `0x4197B1D8` |
| `ZDKMedia_Song_GetGenre` | `0x4197B1E8` |
| `ZDKMedia_Song_GetDuration` | `0x4197B1F8` |
| `ZDKMedia_Song_GetTrackNumber` | `0x4197B224` |
| `ZDKMedia_Song_GetRating` | `0x4197B250` |
| `ZDKMedia_Song_GetPlayCount` | `0x4197B27C` |
| `ZDKMedia_Song_IsDrmProtected` | `0x4197B2A8` |
| `ZDKMedia_Artist_GetSongs` | `0x4197B2D0` |
| `ZDKMedia_Artist_GetAlbums` | `0x4197B2E0` |
| `ZDKMedia_Album_GetGenre` | `0x4197B2F0` |
| `ZDKMedia_Album_GetSongs` | `0x4197B38C` |
| `ZDKMedia_Album_GetDuration` | `0x4197B39C` |
| `ZDKMedia_Album_HasAlbumArt` | `0x4197B3A8` |
| `ZDKMedia_Playlist_GetSongs` | `0x4197B3C8` |
| `ZDKMedia_Playlist_GetDuration` | `0x4197B3D8` |
| `ZDKMedia_Genre_GetAlbums` | `0x4197B3E4` |
| `ZDKMedia_Genre_GetSongs` | `0x4197B3F4` |
| `ZDKMedia_Picture_GetDate` | `0x4197B404` |
| `ZDKMedia_Album_GetThumbnail` | `0x4197B430` |
| `ZDKMedia_Picture_GetThumbnail` | `0x4197B430` |
| `ZDKMedia_PictureAlbum_GetChildAlbums` | `0x4197B434` |
| `ZDKMedia_PictureAlbum_GetParentAlbum` | `0x4197B444` |
| `ZDKMedia_Picture_GetAlbum` | `0x4197B444` |
| `ZDKMedia_PictureAlbum_GetPictures` | `0x4197B454` |
| `ZDKMedia_Shutdown` | `0x4197B8E0` |
| `ZDKMedia_Queue_PlaySongFromFile` | `0x4197B930` |
| `ZDKMedia_Item_GetType` | `0x4197BE50` |
| `ZDKMedia_Album_GetTexture` | `0x4197BF04` |
| `ZDKMedia_Picture_GetTexture` | `0x4197BF04` |
| `ZDKMedia_Picture_GetDimensions` | `0x4197BF08` |
| `ZDKSystem_HeapCreate` | `0x4197C094` |
| `ZDKSystem_HeapDestroy` | `0x4197C098` |
| `ZDKSystem_HeapAlloc` | `0x4197C09C` |
| `ZDKSystem_HeapReAlloc` | `0x4197C0A0` |
| `ZDKSystem_HeapFree` | `0x4197C1A0` |
| `ZDKSystem_HeapSize` | `0x4197C1A4` |
| `ZDKSystem_HeapCompact` | `0x4197C1A8` |
| `ZDKSystem_GetProcessHeap` | `0x4197C1AC` |
| `ZDKSystem_VirtualAlloc` | `0x4197C1B0` |
| `ZDKSystem_VirtualFree` | `0x4197C204` |
| `ZDKSystem_VirtualAllocGetSize` | `0x4197C240` |
| `ZDKSystem_MapFile` | `0x4197C25C` |
| `ZDKSystem_UnmapFile` | `0x4197C454` |
| `ZDKSystem_ShowMessageBox` | `0x4197C4CC` |
| `ZDKSystem_GetMessageBoxState` | `0x4197C5BC` |
| `ZDKSystem_CloseMessageBox` | `0x4197C5CC` |
| `ZDKSystem_ShowKeyboard` | `0x4197C60C` |
| `ZDKSystem_GetKeyboardState` | `0x4197C6A0` |
| `ZDKSystem_SetKeyboardSuggestions` | `0x4197C6C8` |
| `ZDKSystem_GetKeyboardBufferText` | `0x4197C6CC` |
| `ZDKSystem_SetKeyboardBufferText` | `0x4197C6D0` |
| `ZDKSystem_CloseKeyboard` | `0x4197C6D4` |
| `ZDKMedia_Radio_Play` | `0x4197C6F4` |
| `ZDKMedia_Radio_SetPreset` | `0x4197C7F8` |
| `ZDKMedia_Radio_GetPresetCount` | `0x4197C96C` |
| `ZDKMedia_Radio_GetPreset` | `0x4197C9D8` |
| `ZDKSystem_ShowSplashScreen` | `0x4197CB60` |
| `ZDKSystem_CloseNotifyHandle` | `0x4197CB90` |
| `ZDKSystem_GetUserGuid` | `0x4197CBB8` |
| `ZDKSystem_UserGuidToXuid` | `0x4197CC50` |
| `ZDKSystem_GetUserName` | `0x4197CC84` |
| `ZDKSystem_SetTitleName` | `0x4197CD28` |
| `ZDKSystem_ShowGuide` | `0x4197CD70` |
| `ZDKSystem_GetScreenDimensions` | `0x4197CDB4` |
| `ZDKSystem_SetOrientation` | `0x4197CDFC` |
| `ZDKSystem_GetUnlockChallenge` | `0x4197CE68` |
| `ZDKSystem_GetDeviceId` | `0x4197CF4C` |
| `ZDKSystem_LaunchBrowser` | `0x4197CF78` |
| `ZDKSystem_LaunchMarketplaceSearch` | `0x4197D080` |
| `ZDKSystem_GetNotifyHandle` | `0x4197D198` |
| `ZDKSystem_GetNextNotification` | `0x4197D238` |
| `ZDKSystem_GetDiskInfo` | `0x4197D2A0` |
| `ZDKSystem_IsShowingGuide` | `0x4197D35C` |
| `ZDKSystem_GetPowerState` | `0x4197D390` |
| `ZDKSystem_GetLockswitchState` | `0x4197D4B0` |
| `ZDKSystem_SignalUserActivityEx` | `0x4197D4F8` |
| `ZDKSystem_SetUnlockResponse` | `0x4197D55C` |
| `ZDKSystem_SignalUserActivity` | `0x4197D628` |
| `ZDKSystem_GetLocalTimeOffset` | `0x4197D630` |
| `ZDKMedia_Video_SetPlayState` | `0x4197D66C` |
| `ZDKMedia_Video_GetPlayState` | `0x4197D6BC` |
| `ZDKMedia_Video_SetPlayPosition` | `0x4197D768` |
| `ZDKMedia_Video_GetPlayPosition` | `0x4197D78C` |
| `ZDKMedia_Video_PlayVideoFromFile` | `0x4197D7B0` |
| `ZDKInput_Initialize` | `0x4197DA88` |
| `ZDKInput_Shutdown` | `0x4197DA88` |
| `ZDKMedia_Initialize` | `0x4197DA88` |
| `ZDKGL_Initialize` | `0x4197FC38` |
| `ZDKGL_Cleanup` | `0x4197FC58` |
| `ZDKGL_glActiveTexture` | `0x4197FC78` |
| `ZDKGL_glAttachShader` | `0x4197FC9C` |
| `ZDKGL_glBindAttribLocation` | `0x4197FCA0` |
| `ZDKGL_glBindBuffer` | `0x4197FCA4` |
| `ZDKGL_glBindFramebuffer` | `0x4197FCD4` |
| `ZDKGL_glBindRenderbuffer` | `0x4197FCD8` |
| `ZDKGL_glBindTexture` | `0x4197FD08` |
| `ZDKGL_glBlendColor` | `0x4197FD38` |
| `ZDKGL_glBlendEquation` | `0x4197FD3C` |
| `ZDKGL_glBlendEquationSeparate` | `0x4197FD40` |
| `ZDKGL_glBlendFunc` | `0x4197FD44` |
| `ZDKGL_glBlendFuncSeparate` | `0x4197FD48` |
| `ZDKGL_glBufferData` | `0x4197FD4C` |
| `ZDKGL_glBufferSubData` | `0x4197FD94` |
| `ZDKGL_glCheckFramebufferStatus` | `0x4197FD98` |
| `ZDKGL_glClear` | `0x4197FD9C` |
| `ZDKGL_glClearColor` | `0x4197FDA0` |
| `ZDKGL_glClearDepthf` | `0x4197FDA4` |
| `ZDKGL_glClearStencil` | `0x4197FDA8` |
| `ZDKGL_glColorMask` | `0x4197FDAC` |
| `ZDKGL_glCompileShader` | `0x4197FDE4` |
| `ZDKGL_glCompressedTexImage2D` | `0x4197FDE8` |
| `ZDKGL_glCompressedTexSubImage2D` | `0x4197FE60` |
| `ZDKGL_glCopyTexImage2D` | `0x4197FE9C` |
| `ZDKGL_glCopyTexSubImage2D` | `0x4197FED0` |
| `ZDKGL_glCreateProgram` | `0x4197FF04` |
| `ZDKGL_glCreateShader` | `0x4197FF2C` |
| `ZDKGL_glCullFace` | `0x4197FF54` |
| `ZDKGL_glDeleteBuffers` | `0x4197FF58` |
| `ZDKGL_glDeleteFramebuffers` | `0x4197FF8C` |
| `ZDKGL_glDeleteProgram` | `0x4197FFC0` |
| `ZDKGL_glDeleteRenderbuffers` | `0x4197FFE8` |
| `ZDKGL_glDeleteShader` | `0x4198001C` |
| `ZDKGL_glDeleteTextures` | `0x41980044` |
| `ZDKGL_glDepthFunc` | `0x41980078` |
| `ZDKGL_glDepthMask` | `0x4198007C` |
| `ZDKGL_glDepthRangef` | `0x41980090` |
| `ZDKGL_glDetachShader` | `0x41980094` |
| `ZDKGL_glDisable` | `0x41980098` |
| `ZDKGL_glDisableVertexAttribArray` | `0x4198009C` |
| `ZDKGL_glDrawArrays` | `0x419800A0` |
| `ZDKGL_glDrawElements` | `0x419800A4` |
| `ZDKGL_glDrawRangeElements` | `0x419800A8` |
| `ZDKGL_glEnable` | `0x419800CC` |
| `ZDKGL_glEnableVertexAttribArray` | `0x419800D0` |
| `ZDKGL_glFinish` | `0x419800D4` |
| `ZDKGL_glFlush` | `0x419800D8` |
| `ZDKGL_glFramebufferRenderbuffer` | `0x419800DC` |
| `ZDKGL_glFramebufferTexture2D` | `0x419800E0` |
| `ZDKGL_glFrontFace` | `0x419800FC` |
| `ZDKGL_glGenBuffers` | `0x41980100` |
| `ZDKGL_glGenerateMipmap` | `0x4198012C` |
| `ZDKGL_glGenFramebuffers` | `0x41980158` |
| `ZDKGL_glGenRenderbuffers` | `0x41980184` |
| `ZDKGL_glGenTextures` | `0x419801B0` |
| `ZDKGL_glGetActiveAttrib` | `0x419801DC` |
| `ZDKGL_glGetActiveUniform` | `0x41980208` |
| `ZDKGL_glGetAttachedShaders` | `0x41980234` |
| `ZDKGL_glGetAttribLocation` | `0x41980238` |
| `ZDKGL_glGetBooleanv` | `0x4198023C` |
| `ZDKGL_glGetBufferParameteriv` | `0x41980240` |
| `ZDKGL_glGetError` | `0x41980244` |
| `ZDKGL_glGetFloatv` | `0x41980248` |
| `ZDKGL_glGetFramebufferAttachmentParameteriv` | `0x4198024C` |
| `ZDKGL_glGetIntegerv` | `0x41980250` |
| `ZDKGL_glGetProgramiv` | `0x41980254` |
| `ZDKGL_glGetProgramInfoLog` | `0x41980258` |
| `ZDKGL_glGetRenderbufferParameteriv` | `0x4198025C` |
| `ZDKGL_glGetShaderiv` | `0x41980260` |
| `ZDKGL_glGetShaderInfoLog` | `0x41980264` |
| `ZDKGL_glGetShaderPrecisionFormat` | `0x41980268` |
| `ZDKGL_glGetTexParameterfv` | `0x419802C0` |
| `ZDKGL_glGetTexParameteriv` | `0x419802C4` |
| `ZDKGL_glGetUniformfv` | `0x419802C8` |
| `ZDKGL_glGetUniformiv` | `0x419802CC` |
| `ZDKGL_glGetUniformLocation` | `0x419802D0` |
| `ZDKGL_glGetVertexAttribfv` | `0x419802D4` |
| `ZDKGL_glGetVertexAttribiv` | `0x419802D8` |
| `ZDKGL_glGetVertexAttribPointerv` | `0x419802DC` |
| `ZDKGL_glHint` | `0x419802E0` |
| `ZDKGL_glIsBuffer` | `0x419802E4` |
| `ZDKGL_glIsEnabled` | `0x419802E8` |
| `ZDKGL_glIsFramebuffer` | `0x419802EC` |
| `ZDKGL_glIsProgram` | `0x419802F0` |
| `ZDKGL_glIsRenderbuffer` | `0x419802F4` |
| `ZDKGL_glIsShader` | `0x419802F8` |
| `ZDKGL_glIsTexture` | `0x419802FC` |
| `ZDKGL_glLineWidth` | `0x41980300` |
| `ZDKGL_glLinkProgram` | `0x41980304` |
| `ZDKGL_glPixelStorei` | `0x41980308` |
| `ZDKGL_glPolygonOffset` | `0x4198030C` |
| `ZDKGL_glReadPixels` | `0x41980310` |
| `ZDKGL_glReleaseShaderCompiler` | `0x4198033C` |
| `ZDKGL_glRenderbufferStorage` | `0x41980340` |
| `ZDKGL_glSampleCoverage` | `0x41980398` |
| `ZDKGL_glScissor` | `0x419803AC` |
| `ZDKGL_glShaderBinary` | `0x419803B0` |
| `ZDKGL_glShaderSource` | `0x419803CC` |
| `ZDKGL_glStencilFunc` | `0x419803D0` |
| `ZDKGL_glStencilFuncSeparate` | `0x419803D4` |
| `ZDKGL_glStencilMask` | `0x419803D8` |
| `ZDKGL_glStencilMaskSeparate` | `0x419803DC` |
| `ZDKGL_glStencilOp` | `0x419803E0` |
| `ZDKGL_glStencilOpSeparate` | `0x419803E4` |
| `ZDKGL_glTexImage2D` | `0x419803E8` |
| `ZDKGL_glTexParameterf` | `0x41980478` |
| `ZDKGL_glTexParameterfv` | `0x4198047C` |
| `ZDKGL_glTexParameteri` | `0x41980480` |
| `ZDKGL_glTexParameteriv` | `0x41980484` |
| `ZDKGL_glTexSubImage2D` | `0x41980488` |
| `ZDKGL_glUniform1f` | `0x419804C4` |
| `ZDKGL_glUniform1fv` | `0x419804C8` |
| `ZDKGL_glUniform1i` | `0x419804CC` |
| `ZDKGL_glUniform1iv` | `0x419804D0` |
| `ZDKGL_glUniform2f` | `0x419804D4` |
| `ZDKGL_glUniform2fv` | `0x419804D8` |
| `ZDKGL_glUniform2i` | `0x419804DC` |
| `ZDKGL_glUniform2iv` | `0x419804E0` |
| `ZDKGL_glUniform3f` | `0x419804E4` |
| `ZDKGL_glUniform3fv` | `0x419804E8` |
| `ZDKGL_glUniform3i` | `0x419804EC` |
| `ZDKGL_glUniform3iv` | `0x419804F0` |
| `ZDKGL_glUniform4f` | `0x419804F4` |
| `ZDKGL_glUniform4fv` | `0x41980510` |
| `ZDKGL_glUniform4i` | `0x41980514` |
| `ZDKGL_glUniform4iv` | `0x41980530` |
| `ZDKGL_glUniformMatrix2fv` | `0x41980534` |
| `ZDKGL_glUniformMatrix3fv` | `0x41980548` |
| `ZDKGL_glUniformMatrix4fv` | `0x4198055C` |
| `ZDKGL_glUseProgram` | `0x41980570` |
| `ZDKGL_glValidateProgram` | `0x41980574` |
| `ZDKGL_glVertexAttrib1f` | `0x41980578` |
| `ZDKGL_glVertexAttrib1fv` | `0x4198057C` |
| `ZDKGL_glVertexAttrib2f` | `0x41980580` |
| `ZDKGL_glVertexAttrib2fv` | `0x41980584` |
| `ZDKGL_glVertexAttrib3f` | `0x41980588` |
| `ZDKGL_glVertexAttrib3fv` | `0x4198058C` |
| `ZDKGL_glVertexAttrib4f` | `0x41980590` |
| `ZDKGL_glVertexAttrib4fv` | `0x419805AC` |
| `ZDKGL_glVertexAttribPointer` | `0x419805B0` |
| `ZDKGL_glViewport` | `0x41980644` |
| `ZDKGL_BeginDraw` | `0x41980648` |
| `ZDKGL_EndDraw` | `0x41980670` |
| `ZDKFont_Initialize` | `0x419811B0` |
| `ZDKFont_Cleanup` | `0x419811C4` |
| `ZDKFont_Create` | `0x419811D0` |
| `ZDKFont_Destroy` | `0x4198125C` |
| `ZDKFont_GetFontMetrics` | `0x41981274` |
| `ZDKFont_GetCharMetrics` | `0x41981318` |
| `ZDKFont_DrawCharToBuffer` | `0x419813CC` |

## `zd3d.dll` — 62 exports

| Export | VA |
|---|---|
| `D3DXMatrixInverse` | `0x41935C34` |
| `D3DXMatrixRotationQuaternion` | `0x41936648` |
| `D3DXMatrixRotationZ` | `0x419367F8` |
| `D3DXMatrixScaling` | `0x419368CC` |
| `D3DXMatrixTranslation` | `0x4193694C` |
| `D3DXMatrixTranspose` | `0x419369CC` |
| `D3DXPlaneFromPointNormal` | `0x41936A74` |
| `D3DXQuaternionSlerp` | `0x41936AC8` |
| `D3DXQuaternionRotationYawPitchRoll` | `0x41936D10` |
| `D3DXVec2Normalize` | `0x41936E64` |
| `D3DXVec3Normalize` | `0x41936F8C` |
| `D3DXVec2TransformCoord` | `0x419370F0` |
| `D3DXVec3TransformCoord` | `0x419370F4` |
| `D3DXQuaternionRotationAxis` | `0x419370F8` |
| `D3DXMatrixMultiply` | `0x4193717C` |
| `D3DDevice_AddRef` | `0x419376B8` |
| `D3DDevice_BeginScene` | `0x419376D0` |
| `D3DDevice_Clear` | `0x419376E0` |
| `D3DDevice_DrawIndexedVerticesUP` | `0x419376F4` |
| `D3DDevice_DrawVerticesUP` | `0x41937730` |
| `D3DDevice_EndScene` | `0x41937758` |
| `D3DDevice_GetBackBuffer` | `0x41937768` |
| `D3DDevice_GetDeviceCaps` | `0x41937798` |
| `D3DDevice_GetPixelShader` | `0x419377A8` |
| `D3DDevice_GetRenderTarget` | `0x419377E0` |
| `D3DDevice_GetScissorRect` | `0x41937820` |
| `D3DDevice_GetTexture` | `0x41937834` |
| `D3DDevice_GetVertexShader` | `0x41937874` |
| `D3DDevice_GetViewport` | `0x419378AC` |
| `D3DDevice_Present` | `0x419378BC` |
| `D3DDevice_Reset` | `0x419378D4` |
| `D3DDevice_SetClipPlane` | `0x419378F0` |
| `D3DDevice_SetPixelShader` | `0x41937900` |
| `D3DDevice_SetPixelShaderConstantFN` | `0x41937910` |
| `D3DDevice_SetRenderTarget` | `0x41937920` |
| `D3DDevice_SetRenderTarget_External` | `0x41937920` |
| `D3DDevice_SetScissorRect` | `0x4193793C` |
| `D3DDevice_SetTexture` | `0x41937950` |
| `D3DDevice_SetVertexDeclaration` | `0x41937964` |
| `D3DDevice_SetVertexShader` | `0x4193797C` |
| `D3DDevice_SetVertexShaderConstantFN` | `0x4193798C` |
| `D3DDevice_SetViewport` | `0x4193799C` |
| `D3DResource_AddRef` | `0x419379AC` |
| `D3DBaseTexture_GetLevelCount` | `0x419379C4` |
| `D3DSurface_GetContainer` | `0x419379CC` |
| `D3DTexture_GetLevelDesc` | `0x419379E4` |
| `D3DTexture_GetSurfaceLevel` | `0x419379EC` |
| `D3DTexture_LockRect` | `0x41937A1C` |
| `D3DTexture_UnlockRect` | `0x41937A2C` |
| `D3DDevice_Release` | `0x41937A30` |
| `D3DResource_Release` | `0x41937A68` |
| `D3DDevice_CreateTexture` | `0x41937AE0` |
| `D3DDevice_CreatePixelShader` | `0x41937B84` |
| `D3DDevice_CreateVertexShader` | `0x41937BE8` |
| `D3DDevice_CreateVertexDeclaration` | `0x41937C4C` |
| `Direct3D_CreateDevice` | `0x41937CA4` |
| `D3DDevice_CreateQueryTiled` | `0x41937E1C` |
| `D3DDevice_TestCooperativeLevel` | `0x41937E1C` |
| `D3DDevice_SetPixelShaderConstantF_ParameterCheck` | `0x41937E24` |
| `D3DDevice_SetRenderState_ParameterCheck` | `0x41937E24` |
| `D3DDevice_SetSamplerState_ParameterCheck` | `0x41937E24` |
| `D3DDevice_SetVertexShaderConstantF_ParameterCheck` | `0x41937E24` |

## `zrender.dll` — 3 exports

| Export | VA |
|---|---|
| `ZGetCharWidth32` | `0x41BD28A4` |
| `ZGetCharABCWidths` | `0x41BD293C` |
| `ZExtTextOut` | `0x41BD32E8` |

## `zdknet.dll` — 38 exports

| Export | VA |
|---|---|
| `ZDKNet_Initialize` | `0x419584FC` |
| `ZDKNet_Shutdown` | `0x4195850C` |
| `ZDKNet_DoWork` | `0x4195851C` |
| `ZDKNet_SetCallbackInterface` | `0x4195852C` |
| `ZDKNet_GetCallbackInterface` | `0x41958544` |
| `ZDKNet_SetParam` | `0x41958554` |
| `ZDKNet_GetParam` | `0x41958574` |
| `ZDKNet_SetSessionType` | `0x4195858C` |
| `ZDKNet_GetSessionType` | `0x419585A4` |
| `ZDKNet_GetSessionState` | `0x419585B4` |
| `ZDKNet_GetLastStateResult` | `0x419585C4` |
| `ZDKNet_SearchForGames` | `0x419585DC` |
| `ZDKNet_IsSearchComplete` | `0x4195865C` |
| `ZDKNet_GetSearchResult` | `0x4195866C` |
| `ZDKNet_GetSearchResultsCount` | `0x41958684` |
| `ZDKNet_AbortSearch` | `0x41958694` |
| `ZDKNet_HostGame` | `0x419586AC` |
| `ZDKNet_JoinGame` | `0x41958734` |
| `ZDKNet_LeaveGame` | `0x4195874C` |
| `ZDKNet_StartGame` | `0x4195875C` |
| `ZDKNet_EndGame` | `0x4195876C` |
| `ZDKNet_SetGameProperty` | `0x4195877C` |
| `ZDKNet_GetGameProperty` | `0x41958794` |
| `ZDKNet_SetPlayerSlots` | `0x419587B4` |
| `ZDKNet_GetPlayerSlots` | `0x419587CC` |
| `ZDKNet_IsHost` | `0x419587DC` |
| `ZDKNet_DropPlayer` | `0x419587EC` |
| `ZDKNet_GetHostPlayer` | `0x41958804` |
| `ZDKNet_GetLocalPlayer` | `0x41958814` |
| `ZDKNet_GetPlayerBySlot` | `0x41958824` |
| `ZDKNet_GetPlayerCount` | `0x4195883C` |
| `ZDKNet_GetQueuedPayloadCount` | `0x4195884C` |
| `ZDKNet_RequestLocalPlayerReady` | `0x41958864` |
| `ZDKNet_ResetReady` | `0x4195887C` |
| `ZDKNet_IsEveryoneReady` | `0x4195888C` |
| `ZDKNet_SendDataToAll` | `0x4195889C` |
| `ZDKNet_SendDataToPlayer` | `0x419588C4` |
| `ZDKNet_GetNetworkStats` | `0x419588FC` |

## `zserial.dll` — 18 exports

| Export | VA |
|---|---|
| `COM_MtpInit` | `0x41B31770` |
| `COM_MtpDeinit` | `0x41B31774` |
| `COM_MtpTXGetData` | `0x41B31778` |
| `COM_MtpRXPutData` | `0x41B3177C` |
| `COM_MtpSetFlags` | `0x41B31780` |
| `COM_MtpGetFlags` | `0x41B31784` |
| `COM_PreClose` | `0x41B323A8` |
| `COM_Close` | `0x41B3245C` |
| `COM_PreDeinit` | `0x41B32580` |
| `COM_Deinit` | `0x41B325E0` |
| `COM_Read` | `0x41B32700` |
| `COM_Seek` | `0x41B32A78` |
| `COM_PowerUp` | `0x41B32A80` |
| `COM_PowerDown` | `0x41B32AB8` |
| `COM_IOControl` | `0x41B32C8C` |
| `COM_Init` | `0x41B33A8C` |
| `COM_Open` | `0x41B33D0C` |
| `COM_Write` | `0x41B33FA0` |

## `zmassive.dll` — 19 exports

| Export | VA |
|---|---|
| `MACL_Initialize` | `0x419F20B0` |
| `MACL_Shutdown` | `0x419F222C` |
| `MACL_Update` | `0x419F2270` |
| `MACL_HasWorkPending` | `0x419F2280` |
| `MACL_CreatePlacement` | `0x419F2284` |
| `MACL_RemovePlacement` | `0x419F2294` |
| `MACL_RemoveAllPlacements` | `0x419F22A4` |
| `MACL_RequestAdsForPlacements` | `0x419F22B4` |
| `MACL_SetActivityForPlacement` | `0x419F22C4` |
| `MACL_GetClickThroughURLForPlacement` | `0x419F22D4` |
| `MACL_FlushActivityReports` | `0x419F22D8` |
| `MACL_SuspendNetworkActivity` | `0x419F22E8` |
| `MACL_ResumeNetworkActivity` | `0x419F22F8` |
| `MACL_EnableNetworkActivity` | `0x419F2308` |
| `MACL_DisableNetworkActivity` | `0x419F2318` |
| `MACL_SetMaxSendKBPS` | `0x419F2328` |
| `MACL_SetMaxReceiveKBPS` | `0x419F2338` |
| `MACL_SetCustomMemoryBuffer` | `0x419F2348` |
| `MACL_ReleaseCustomMemoryBuffer` | `0x419F2358` |

## `zlib.dll` — 18 exports

| Export | VA |
|---|---|
| `adler32` | `0x404230E4` |
| `get_crc_table` | `0x404232E4` |
| `crc32` | `0x40423308` |
| `deflate` | `0x404234B8` |
| `deflateEnd` | `0x40423868` |
| `deflateReset` | `0x404247E4` |
| `deflateInit2_` | `0x4042490C` |
| `deflateInit_` | `0x40424B8C` |
| `inflateReset` | `0x40424BCC` |
| `inflateEnd` | `0x40424C34` |
| `inflateInit2_` | `0x40424CA0` |
| `inflateInit_` | `0x40424DD8` |
| `inflate` | `0x40424DF4` |
| `inflateSetDictionary` | `0x40425358` |
| `inflateSync` | `0x40425400` |
| `inflateSyncPoint` | `0x404254FC` |
| `uncompress` | `0x4042553C` |
| `zlibVersion` | `0x404255E0` |

## `zcab.dll` — 12 exports

| Export | VA |
|---|---|
| `ZCabClose` | `0x418B2338` |
| `ZCabGetFileCount` | `0x418B23DC` |
| `ZCabGetFileName` | `0x418B2458` |
| `ZCabCreateReadFileStream` | `0x418B24CC` |
| `ZCabOpen` | `0x418B27A8` |
| `ZCabFreeCertificateData` | `0x418B2954` |
| `ZCabGetCertificateData` | `0x418B2984` |
| `ZCabVerifyFileSignature` | `0x418B45FC` |
| `ZCabExtractSingleFileToPathEx` | `0x418B5380` |
| `ZCabExtractSingleFileToPath` | `0x418B573C` |
| `ZCabExtractAllFilesToPathEx` | `0x418B5A80` |
| `ZCabExtractAllFilesToPath` | `0x418B5D70` |

## `ziehooks.dll` — 6 exports

| Export | VA |
|---|---|
| `Urlmon_IsDialogBoxHandled` | `0x41C110A4` |
| `Wininet_IsMessageBoxHandled` | `0x41C112A4` |
| `Wininet_IsDialogBoxHandled` | `0x41C1135C` |
| `MemoryHooks_ApplyAllocationByteCountChange` | `0x41C11494` |
| `MemoryHooks_RevertAllocationByteCountChange` | `0x41C11570` |
| `MemoryHooks_GetAllocationPageCountAvailable` | `0x41C11574` |

## `zcontent_serv.dll` — 5 exports

| Export | VA |
|---|---|
| `ZCN_Init` | `0x418D5220` |
| `ZCN_Deinit` | `0x418D5238` |
| `ZCN_Close` | `0x418D5248` |
| `ZCN_Open` | `0x418D5248` |
| `ZCN_IOControl` | `0x418D5258` |

## `zmedia_serv.dll` — 5 exports

| Export | VA |
|---|---|
| `ZME_Init` | `0x41A17768` |
| `ZME_Deinit` | `0x41A17794` |
| `ZME_Close` | `0x41A177A8` |
| `ZME_Open` | `0x41A177A8` |
| `ZME_IOControl` | `0x41A177B8` |

## `zhud_serv.dll` — 5 exports

| Export | VA |
|---|---|
| `ZHD_Init` | `0x419B5464` |
| `ZHD_Deinit` | `0x419B5500` |
| `ZHD_IOControl` | `0x419B5588` |
| `ZHD_Close` | `0x419D3524` |
| `ZHD_Open` | `0x419D3524` |

## `znet_serv.dll` — 5 exports

| Export | VA |
|---|---|
| `ZNE_Init` | `0x41A6F294` |
| `ZNE_Deinit` | `0x41A6F2AC` |
| `ZNE_IOControl` | `0x41A6F2C8` |
| `ZNE_Close` | `0x41A73F40` |
| `ZNE_Open` | `0x41A73F40` |

## `zconfig_serv.dll` — 5 exports

| Export | VA |
|---|---|
| `ZCO_Init` | `0x418F2054` |
| `ZCO_Deinit` | `0x418F20C8` |
| `ZCO_Close` | `0x418F20D8` |
| `ZCO_Open` | `0x418F20D8` |
| `ZCO_IOControl` | `0x418F20E8` |

## `zcredentials_serv.dll` — 5 exports

| Export | VA |
|---|---|
| `ZCR_Init` | `0x419011FC` |
| `ZCR_Deinit` | `0x41901214` |
| `ZCR_Close` | `0x41901224` |
| `ZCR_Open` | `0x41901224` |
| `ZCR_IOControl` | `0x41901234` |

## `compclient.dll` — 190 exports

| Export | VA |
|---|---|
| `long int __cdecl CompDisplay_Show(int)` | `0x41BB12B8` |
| `long int __cdecl CompDisplay_Snapshot(struct tagRECT const *, int, void *, unsigned int)` | `0x41BB1388` |
| `long int __cdecl CompWindow_Show(struct HWND__ *)` | `0x41BB1540` |
| `long int __cdecl CompWindow_Hide(struct HWND__ *)` | `0x41BB1598` |
| `long int __cdecl CompWindow_ChangeZOrder(struct HWND__ *, int)` | `0x41BB15F4` |
| `long int __cdecl CompWindow_Close(struct HWND__ *)` | `0x41BB164C` |
| `long int __cdecl CompWindow_BeginAnimation(struct HWND__ *)` | `0x41BB16A0` |
| `long int __cdecl CompWindow_EndAndRunAnimation(struct HWND__ *)` | `0x41BB16F4` |
| `long int __cdecl CompWindow_EndAndSetAnimation(struct HWND__ *, enum WindowOp)` | `0x41BB1748` |
| `long int __cdecl CompWindow_SetOpacity(struct HWND__ *, float, float)` | `0x41BB17A0` |
| `long int __cdecl CompWindow_SetPosition(struct HWND__ *, float, float, float)` | `0x41BB181C` |
| `long int __cdecl CompWindow_SetUnitOrigin(struct HWND__ *, float, float, float)` | `0x41BB18A8` |
| `long int __cdecl CompWindow_SetScale(struct HWND__ *, float, float, float)` | `0x41BB1934` |
| `long int __cdecl CompWindow_SetRotation(struct HWND__ *, float, float)` | `0x41BB19C0` |
| `long int __cdecl CompWindow_AlphaBlend(struct HWND__ *, int)` | `0x41BB1A3C` |
| `eglQueryString` | `0x41BB1D34` |
| `eglBindAPI` | `0x41BB1E54` |
| `eglGetCurrentContext` | `0x41BB1E6C` |
| `eglGetError` | `0x41BB1F0C` |
| `eglGetDisplay` | `0x41BB1FF0` |
| `eglInitialize` | `0x41BB20C4` |
| `eglTerminate` | `0x41BB21F0` |
| `eglGetConfigs` | `0x41BB2270` |
| `eglChooseConfig` | `0x41BB2340` |
| `eglGetConfigAttrib` | `0x41BB246C` |
| `eglCreateWindowSurface` | `0x41BB2590` |
| `eglCreatePbufferSurface` | `0x41BB26D4` |
| `eglCreatePixmapSurface` | `0x41BB2810` |
| `eglDestroySurface` | `0x41BB2954` |
| `eglQuerySurface` | `0x41BB2A30` |
| `eglQueryAPI` | `0x41BB2B54` |
| `eglWaitClient` | `0x41BB2C2C` |
| `eglReleaseThread` | `0x41BB2CFC` |
| `eglCreatePbufferFromClientBuffer` | `0x41BB2DCC` |
| `eglSurfaceAttrib` | `0x41BB2F2C` |
| `eglBindTexImage` | `0x41BB3018` |
| `eglReleaseTexImage` | `0x41BB30FC` |
| `eglSwapInterval` | `0x41BB31E0` |
| `eglCreateContext` | `0x41BB32BC` |
| `eglDestroyContext` | `0x41BB3464` |
| `eglMakeCurrent` | `0x41BB3578` |
| `eglGetCurrentSurface` | `0x41BB367C` |
| `eglGetCurrentDisplay` | `0x41BB3750` |
| `eglQueryContext` | `0x41BB3820` |
| `eglWaitGL` | `0x41BB3950` |
| `eglWaitNative` | `0x41BB3A20` |
| `eglSwapBuffers` | `0x41BB3AF4` |
| `eglCopyBuffers` | `0x41BB3BD0` |
| `glGetError` | `0x41BB3DEC` |
| `glDisableVertexAttribArray` | `0x41BB3E10` |
| `glEnableVertexAttribArray` | `0x41BB4234` |
| `glGenBuffers` | `0x41BB4258` |
| `glGenFramebuffers` | `0x41BB434C` |
| `glGenRenderbuffers` | `0x41BB4440` |
| `glGenTextures` | `0x41BB4534` |
| `glGetActiveAttrib` | `0x41BB4628` |
| `glGetActiveUniform` | `0x41BB4750` |
| `glGetAttachedShaders` | `0x41BB4878` |
| `glGetBooleanv` | `0x41BB4968` |
| `glGetBufferParameteriv` | `0x41BB4A40` |
| `glGetFloatv` | `0x41BB4B20` |
| `glGetFramebufferAttachmentParameteriv` | `0x41BB4C04` |
| `glGetIntegerv` | `0x41BB4CF8` |
| `glGetProgramInfoLog` | `0x41BB4DDC` |
| `glGetRenderbufferParameteriv` | `0x41BB4EE0` |
| `glGetShaderInfoLog` | `0x41BB4FBC` |
| `glGetShaderPrecisionFormat` | `0x41BB50C4` |
| `glGetString` | `0x41BB51B4` |
| `glGetTexParameterfv` | `0x41BB52DC` |
| `glGetTexParameteriv` | `0x41BB53C8` |
| `glGetUniformfv` | `0x41BB54A8` |
| `glGetUniformiv` | `0x41BB5594` |
| `glGetVertexAttribfv` | `0x41BB5680` |
| `glGetVertexAttribiv` | `0x41BB576C` |
| `glGetVertexAttribPointerv` | `0x41BB589C` |
| `glReadPixels` | `0x41BB58C0` |
| `glVertexAttribPointer` | `0x41BB5A8C` |
| `glActiveTexture` | `0x41BB5ACC` |
| `glAttachShader` | `0x41BB5B44` |
| `glBindAttribLocation` | `0x41BB5BC4` |
| `glBindBuffer` | `0x41BB5C7C` |
| `glBindFramebuffer` | `0x41BB5D5C` |
| `glBindRenderbuffer` | `0x41BB5DDC` |
| `glBindTexture` | `0x41BB5E5C` |
| `glBlendColor` | `0x41BB5EDC` |
| `glBlendEquation` | `0x41BB5F98` |
| `glBlendEquationSeparate` | `0x41BB6010` |
| `glBlendFunc` | `0x41BB6090` |
| `glBlendFuncSeparate` | `0x41BB6110` |
| `glBufferData` | `0x41BB61A0` |
| `glBufferSubData` | `0x41BB62A4` |
| `glCheckFramebufferStatus` | `0x41BB6354` |
| `glClear` | `0x41BB6434` |
| `glClearColor` | `0x41BB64AC` |
| `glClearDepthf` | `0x41BB6568` |
| `glClearStencil` | `0x41BB65F4` |
| `glColorMask` | `0x41BB666C` |
| `glCompileShader` | `0x41BB6700` |
| `glCompressedTexImage2D` | `0x41BB6974` |
| `glCompressedTexSubImage2D` | `0x41BB69C8` |
| `glCopyTexImage2D` | `0x41BB6A1C` |
| `glCopyTexSubImage2D` | `0x41BB6ACC` |
| `glCreateProgram` | `0x41BB6B7C` |
| `glCreateShader` | `0x41BB6C4C` |
| `glCullFace` | `0x41BB6D20` |
| `glDeleteBuffers` | `0x41BB6D98` |
| `glDeleteFramebuffers` | `0x41BB6E4C` |
| `glDeleteProgram` | `0x41BB6F00` |
| `glDeleteRenderbuffers` | `0x41BB6F78` |
| `glDeleteShader` | `0x41BB702C` |
| `glDeleteTextures` | `0x41BB70A4` |
| `glDepthFunc` | `0x41BB7158` |
| `glDepthMask` | `0x41BB71D0` |
| `glDepthRangef` | `0x41BB724C` |
| `glDetachShader` | `0x41BB72E8` |
| `glDisable` | `0x41BB7368` |
| `glDrawArrays` | `0x41BB73E0` |
| `glDrawElements` | `0x41BB7558` |
| `glDrawRangeElements` | `0x41BB776C` |
| `glEnable` | `0x41BB7980` |
| `glFinish` | `0x41BB79F8` |
| `glFlush` | `0x41BB7A6C` |
| `glFramebufferRenderbuffer` | `0x41BB7AE0` |
| `glFramebufferTexture2D` | `0x41BB7B70` |
| `glFrontFace` | `0x41BB7C08` |
| `glGenerateMipmap` | `0x41BB7C80` |
| `glGetAttribLocation` | `0x41BB7CF8` |
| `glGetProgramiv` | `0x41BB7E04` |
| `glGetShaderiv` | `0x41BB7EDC` |
| `glGetUniformLocation` | `0x41BB7FB4` |
| `glHint` | `0x41BB80C0` |
| `glIsBuffer` | `0x41BB8140` |
| `glIsEnabled` | `0x41BB8214` |
| `glIsFramebuffer` | `0x41BB82E8` |
| `glIsProgram` | `0x41BB83BC` |
| `glIsRenderbuffer` | `0x41BB8490` |
| `glIsShader` | `0x41BB8564` |
| `glIsTexture` | `0x41BB8638` |
| `glLineWidth` | `0x41BB870C` |
| `glLinkProgram` | `0x41BB8798` |
| `glPixelStorei` | `0x41BB8810` |
| `glPolygonOffset` | `0x41BB8890` |
| `glReleaseShaderCompiler` | `0x41BB892C` |
| `glRenderbufferStorage` | `0x41BB89A0` |
| `glSampleCoverage` | `0x41BB8A30` |
| `glScissor` | `0x41BB8AC4` |
| `glShaderBinary` | `0x41BB8B54` |
| `glShaderSource` | `0x41BB8C10` |
| `glStencilFunc` | `0x41BB8CF4` |
| `glStencilFuncSeparate` | `0x41BB8D7C` |
| `glStencilMask` | `0x41BB8E0C` |
| `glStencilMaskSeparate` | `0x41BB8E84` |
| `glStencilOp` | `0x41BB8F04` |
| `glStencilOpSeparate` | `0x41BB8F8C` |
| `glTexImage2D` | `0x41BB901C` |
| `glTexParameterf` | `0x41BB9258` |
| `glTexParameterfv` | `0x41BB92F0` |
| `glTexParameteri` | `0x41BB937C` |
| `glTexParameteriv` | `0x41BB9404` |
| `glTexSubImage2D` | `0x41BB9490` |
| `glUniform1f` | `0x41BB95F4` |
| `glUniform1fv` | `0x41BB9684` |
| `glUniform1i` | `0x41BB9744` |
| `glUniform1iv` | `0x41BB97C4` |
| `glUniform2f` | `0x41BB9880` |
| `glUniform2fv` | `0x41BB9920` |
| `glUniform2i` | `0x41BB99E4` |
| `glUniform2iv` | `0x41BB9A6C` |
| `glUniform3f` | `0x41BB9B2C` |
| `glUniform3fv` | `0x41BB9BDC` |
| `glUniform3i` | `0x41BB9CA4` |
| `glUniform3iv` | `0x41BB9D34` |
| `glUniform4f` | `0x41BB9DF8` |
| `glUniform4fv` | `0x41BB9EBC` |
| `glUniform4i` | `0x41BB9F80` |
| `glUniform4iv` | `0x41BBA018` |
| `glUniformMatrix2fv` | `0x41BBA0D8` |
| `glUniformMatrix3fv` | `0x41BBA18C` |
| `glUniformMatrix4fv` | `0x41BBA244` |
| `glUseProgram` | `0x41BBA2F8` |
| `glValidateProgram` | `0x41BBA370` |
| `glVertexAttrib1f` | `0x41BBA3E8` |
| `glVertexAttrib1fv` | `0x41BBA478` |
| `glVertexAttrib2f` | `0x41BBA4FC` |
| `glVertexAttrib2fv` | `0x41BBA59C` |
| `glVertexAttrib3f` | `0x41BBA628` |
| `glVertexAttrib3fv` | `0x41BBA6D8` |
| `glVertexAttrib4f` | `0x41BBA76C` |
| `glVertexAttrib4fv` | `0x41BBA830` |
| `glViewport` | `0x41BBA8CC` |

## `AAXSDKWin.dll` — 74 exports

| Export | VA |
|---|---|
| `AudibleUpdateDRMRecToLightSig` | `0x416010D8` |
| `AudibleCreateActivationRequest` | `0x41601178` |
| `AudibleGenerateActivationFromServerResponse` | `0x416013EC` |
| `AudibleCreateActivationRequestCE` | `0x41601894` |
| `AudibleGetActivationDataFromPC` | `0x41601FC0` |
| `AudibleSetActivationDataToPC` | `0x41601FE0` |
| `AudibleGetUserNameAndPassword` | `0x41601FF8` |
| `AudibleProcessADHFileToDownloadEntry` | `0x41602BE4` |
| `AudibleProcessADHFileToDownloadEntryEx` | `0x41602C68` |
| `public: int __cdecl CAudibleMetadata::CloseAAXFile(void)` | `0x41602D04` |
| `public: void __cdecl CAudibleMetadata::constructor(void)` | `0x41603224` |
| `public: void __cdecl CAudibleMetadata::~destructor(void)` | `0x416034A0` |
| `public: int __cdecl CAudibleMetadata::OpenAAXFile(wchar_t const *)` | `0x41603F4C` |
| `AudibleGetAAFilesPath` | `0x41604268` |
| `AAXOpenFileWinW` | `0x416043E0` |
| `AAXAuthenticateWinCE` | `0x4160444C` |
| `AW_Base64Encode` | `0x416049B0` |
| `AW_Base64Decode` | `0x41604B88` |
| `AAXGetMetadataLocaleList` | `0x41607040` |
| `AAXGetMetadataInfo` | `0x41607134` |
| `AAXGetChapterMetadataInfo` | `0x41607228` |
| `AAXGetChapterInfo` | `0x41607348` |
| `AAXGetLinkInfo` | `0x416073D0` |
| `AAXSetUnicodeFormat` | `0x41607458` |
| `AAXGetUnicodeFormat` | `0x4160746C` |
| `AAXSetMetadataLocale` | `0x41607494` |
| `AAXGetMetadata` | `0x41607668` |
| `AAXGetChapterMetadata` | `0x416077C8` |
| `AAXGetChapterText` | `0x41607940` |
| `AAXGetLink` | `0x41607B18` |
| `AAXGetFileType` | `0x41607E90` |
| `AAXGetDRMType` | `0x41607EB8` |
| `AAXGetAudioType` | `0x41607EE0` |
| `AAXGetSampleRate` | `0x41607F08` |
| `AAXGetAvgBitrate` | `0x41607F30` |
| `AAXGetMaxBitrate` | `0x41607F58` |
| `AAXGetDuration` | `0x41607F80` |
| `AAXGetAudioChannelCount` | `0x41607FA8` |
| `AAXGetChapterCount` | `0x41607FD0` |
| `AAXGetImageCount` | `0x41607FF8` |
| `AAXGetImageStartTime` | `0x41608020` |
| `AAXGetImageInfo` | `0x416082E8` |
| `AAXGetEncodedImage` | `0x4160834C` |
| `AAXGetLinkCount` | `0x416084D0` |
| `AAXGetLinkStartTime` | `0x416084F8` |
| `AAXSetPreferredDisplaySize` | `0x4160857C` |
| `AAXOpenFile` | `0x41608914` |
| `AAXCloseFile` | `0x41608A94` |
| `AAXEnableChapterText` | `0x41608C24` |
| `AAXDisableChapterText` | `0x41608C40` |
| `AAXEnableChapterLinks` | `0x41608C5C` |
| `AAXEnableLinks` | `0x41608C5C` |
| `AAXDisableChapterLinks` | `0x41608C78` |
| `AAXDisableLinks` | `0x41608C78` |
| `AAXEnableChapterImages` | `0x41608C94` |
| `AAXEnableImages` | `0x41608C94` |
| `AAXDisableChapterImages` | `0x41608CB0` |
| `AAXDisableImages` | `0x41608CB0` |
| `AAXSetImageQuality` | `0x41608CCC` |
| `AAXGetImageQuality` | `0x41608CE0` |
| `AAXEnableADTSHeaders` | `0x41608D08` |
| `AAXDisableADTSHeaders` | `0x41608D24` |
| `AAXDecodePCMFrame` | `0x41608EA8` |
| `AAXSavePlaybackPosition` | `0x4160908C` |
| `AAXGetChapterStartTime` | `0x41609114` |
| `AAXSeekToChapter` | `0x4160915C` |
| `AAXSeek` | `0x416091C4` |
| `AAXSkipNextFrame` | `0x41609328` |
| `AAXGetNextFrameInfo` | `0x41609390` |
| `AAXGetPlaybackPosition` | `0x416094B4` |
| `AAXGetEncodedAudio` | `0x41609538` |
| `AAXGetCurrentChapter` | `0x416096F4` |
| `AAXAuthenticate` | `0x41609C98` |
| `AAXGetUseralias` | `0x41609CC8` |

## `libKD.dll` — 173 exports

| Export | VA |
|---|---|
| `kdStateGeti` | `0x415D1000` |
| `kdStateGetl` | `0x415D1034` |
| `kdStateGetf` | `0x415D1068` |
| `kdOutputSeti` | `0x415D109C` |
| `kdOutputSetf` | `0x415D10D0` |
| `kdSetEventInputMultitouchActiveNV` | `0x415D1104` |
| `kdEnableEventInputMultitouchMergeNV` | `0x415D1128` |
| `kdSetEventInputAccelActiveNV` | `0x415D114C` |
| `kdCreateWindow` | `0x415D1180` |
| `kdDestroyWindow` | `0x415D11B4` |
| `kdSetWindowPropertybv` | `0x415D11D8` |
| `kdSetWindowPropertyiv` | `0x415D120C` |
| `kdSetWindowPropertycv` | `0x415D1240` |
| `kdGetWindowPropertybv` | `0x415D1274` |
| `kdGetWindowPropertyiv` | `0x415D12A8` |
| `kdGetWindowPropertycv` | `0x415D12DC` |
| `kdRealizeWindow` | `0x415D1318` |
| `kdSetWMClientUserDataNV` | `0x415D1344` |
| `kdGetWMClientUserDataNV` | `0x415D1370` |
| `kdSetWMClientWindowUserDataNV` | `0x415D1394` |
| `kdGetWMClientWindowUserDataNV` | `0x415D13C0` |
| `kdGetWMClientNV` | `0x415D13E4` |
| `kdWMEventProcessedNV` | `0x415D1408` |
| `kdSetSupportedWindowPropertiesNV` | `0x415D1434` |
| `kdSetSupportedOutputsNV` | `0x415D1460` |
| `kdSetSupportedStatesNV` | `0x415D148C` |
| `kdSetWMClientStateNV` | `0x415D14B8` |
| `kdSetWMClientWindowPropertyNV` | `0x415D14E4` |
| `kdDispatchWindowEventNV` | `0x415D1510` |
| `kdDispatchSystemEventNV` | `0x415D155C` |
| `kdSetWMClientWindowRenderSurfacesNV` | `0x415D15A8` |
| `kdGetWMClientWindowFrontBufferNV` | `0x415D160C` |
| `kdLockWMClientWindowNV` | `0x415D1658` |
| `kdReleaseWMClientWindowNV` | `0x415D167C` |
| `NvKdLaunch` | `0x415D16A0` |
| `kdGetError` | `0x415D1DF4` |
| `kdSetError` | `0x415D1E08` |
| `kdGetTLS` | `0x415D1E20` |
| `kdSetTLS` | `0x415D1E34` |
| `kdLogMessage` | `0x415D1E4C` |
| `kdQueryAttribi` | `0x415D1EE0` |
| `kdQueryAttribcv` | `0x415D1EFC` |
| `kdQueryIndexedAttribcv` | `0x415D1F58` |
| `kdHandleAssertion` | `0x415D1F74` |
| `kdMalloc` | `0x415D201C` |
| `kdFree` | `0x415D2048` |
| `kdRealloc` | `0x415D204C` |
| `kdMemchr` | `0x415D20C4` |
| `kdMemcmp` | `0x415D20EC` |
| `kdMemcpy` | `0x415D20F0` |
| `kdMemmove` | `0x415D2104` |
| `kdMemset` | `0x415D2118` |
| `kdStrchr` | `0x415D2130` |
| `kdStrcmp` | `0x415D2160` |
| `kdStrlen` | `0x415D2164` |
| `kdStrnlen` | `0x415D2168` |
| `kdStrncat_s` | `0x415D2198` |
| `kdStrncmp` | `0x415D221C` |
| `kdStrncpy_s` | `0x415D2220` |
| `kdStrcpy_s` | `0x415D22C0` |
| `kdSetEventUserptr` | `0x415D271C` |
| `kdDefaultEvent` | `0x415D2738` |
| `kdPostEvent` | `0x415D2750` |
| `kdGetEventInputMultitouchDataNV` | `0x415D2A3C` |
| `kdInstallCallback` | `0x415D3074` |
| `kdCreateEvent` | `0x415D3434` |
| `kdPostThreadEvent` | `0x415D3484` |
| `kdFreeEvent` | `0x415D3598` |
| `kdWaitEvent` | `0x415D38F8` |
| `kdPumpEvents` | `0x415D3A4C` |
| `kdSetTimer` | `0x415D3AC8` |
| `kdCancelTimer` | `0x415D3B50` |
| `kdThreadAttrCreate` | `0x415D3EB8` |
| `kdThreadAttrFree` | `0x415D3EEC` |
| `kdThreadAttrSetDetachState` | `0x415D3EFC` |
| `kdThreadAttrSetStackSize` | `0x415D3F2C` |
| `kdThreadCreate` | `0x415D3F5C` |
| `kdThreadDetach` | `0x415D405C` |
| `kdThreadSelf` | `0x415D40A8` |
| `kdThreadMutexCreate` | `0x415D40B8` |
| `kdThreadMutexFree` | `0x415D40F0` |
| `kdThreadMutexLock` | `0x415D4114` |
| `kdThreadMutexUnlock` | `0x415D4124` |
| `kdThreadCondCreate` | `0x415D4134` |
| `kdThreadCondFree` | `0x415D41B0` |
| `kdThreadCondSignal` | `0x415D41D8` |
| `kdThreadCondBroadcast` | `0x415D422C` |
| `kdThreadCondWait` | `0x415D4278` |
| `kdThreadSemCreate` | `0x415D4348` |
| `kdThreadSemFree` | `0x415D4384` |
| `kdThreadSemWait` | `0x415D4398` |
| `kdThreadSemPost` | `0x415D43AC` |
| `kdExit` | `0x415D4474` |
| `kdThreadExit` | `0x415D44B4` |
| `kdThreadJoin` | `0x415D450C` |
| `kdThreadOnce` | `0x415D4584` |
| `kdNameLookup` | `0x415D5240` |
| `kdNameLookupCancel` | `0x415D5398` |
| `kdSocketCreate` | `0x415D5498` |
| `kdSocketClose` | `0x415D55D0` |
| `kdSocketBind` | `0x415D560C` |
| `kdSocketGetName` | `0x415D5774` |
| `kdSocketConnect` | `0x415D5868` |
| `kdSocketListen` | `0x415D5930` |
| `kdSocketAccept` | `0x415D59F4` |
| `kdSocketSend` | `0x415D5B48` |
| `kdSocketSendTo` | `0x415D5BA0` |
| `kdSocketRecv` | `0x415D5C9C` |
| `kdSocketRecvFrom` | `0x415D5CF4` |
| `kdHtonl` | `0x415D5DD4` |
| `kdHtons` | `0x415D5DF0` |
| `kdNtohl` | `0x415D5E14` |
| `kdNtohs` | `0x415D5E30` |
| `kdInetAton` | `0x415D5E54` |
| `kdInetNtop` | `0x415D5E84` |
| `kdGetTimeUST` | `0x415D60F4` |
| `kdGmtime_r` | `0x415D61A4` |
| `kdLocaltime_r` | `0x415D6204` |
| `kdTime` | `0x415D6310` |
| `kdUSTAtEpoch` | `0x415D636C` |
| `kdFopen` | `0x415D68B8` |
| `kdFclose` | `0x415D69C8` |
| `kdFflush` | `0x415D6A10` |
| `kdFread` | `0x415D6A18` |
| `kdFwrite` | `0x415D6AD4` |
| `kdGetc` | `0x415D6CB0` |
| `kdPutc` | `0x415D6CE4` |
| `kdFgets` | `0x415D6D14` |
| `kdFEOF` | `0x415D6D74` |
| `kdFerror` | `0x415D6D88` |
| `kdClearerr` | `0x415D6D9C` |
| `kdFseek` | `0x415D6DAC` |
| `kdFtell` | `0x415D6E48` |
| `kdMkdir` | `0x415D6EBC` |
| `kdRename` | `0x415D6F20` |
| `kdRemove` | `0x415D7150` |
| `kdTruncate` | `0x415D71B0` |
| `kdStat` | `0x415D7358` |
| `kdFstat` | `0x415D7408` |
| `kdAccess` | `0x415D748C` |
| `kdOpenDir` | `0x415D7514` |
| `kdReadDir` | `0x415D7714` |
| `kdCloseDir` | `0x415D781C` |
| `kdGetFree` | `0x415D7888` |
| `kdRmdir` | `0x415D79C8` |
| `kdGetLocale` | `0x415D7C98` |
| `kdCryptoRandom` | `0x415D7D80` |
| `kdAcosf` | `0x415D8FA4` |
| `kdAsinf` | `0x415D8FB0` |
| `kdAtanf` | `0x415D8FBC` |
| `kdCosf` | `0x415D8FD8` |
| `kdSinf` | `0x415D8FE4` |
| `kdTanf` | `0x415D8FF0` |
| `kdExpf` | `0x415D8FFC` |
| `kdLogf` | `0x415D9048` |
| `kdFabsf` | `0x415D9088` |
| `kdPowf` | `0x415D9090` |
| `kdSqrtf` | `0x415D91A0` |
| `kdCeilf` | `0x415D91D4` |
| `kdFloorf` | `0x415D91E0` |
| `kdRoundf` | `0x415D91EC` |
| `kdInvsqrtf` | `0x415D91F8` |
| `kdFmodf` | `0x415D9220` |
| `kdBitsToFloatNV` | `0x415D9280` |
| `kdAtan2f` | `0x415D92B0` |
| `kdAbs` | `0x415D964C` |
| `kdStrtof` | `0x415D9650` |
| `kdStrtol` | `0x415D9BAC` |
| `kdStrtoul` | `0x415D9C38` |
| `kdLtostr` | `0x415D9D24` |
| `kdUltostr` | `0x415D9D48` |
| `kdFtostr` | `0x415D9DB4` |
| `NvKdApiInit` | `0x415DD8CC` |

## `xhttpdll.dll` — 31 exports

| Export | VA |
|---|---|
| `DllMain` | `0x417B5384` |
| `WinHttpCrackUrl` | `0x417B53C0` |
| `WinHttpQueryDataAvailable` | `0x417B53C4` |
| `WinHttpQueryOption` | `0x417B53C8` |
| `WinHttpReadData` | `0x417B53CC` |
| `WinHttpReceiveResponse` | `0x417B53D0` |
| `WinHttpSetOption` | `0x417B53D4` |
| `WinHttpSetStatusCallback` | `0x417B53D8` |
| `WinHttpWriteData` | `0x417B53DC` |
| `WinHttpAddRequestHeaders` | `0x417B53E0` |
| `WinHttpStartup` | `0x417B5614` |
| `WinHttpShutdown` | `0x417B5748` |
| `WinHttpCloseHandle` | `0x417B5780` |
| `XHttpCrackUrl` | `0x417B62A4` |
| `XHttpOpen` | `0x417B84E8` |
| `XHttpCloseHandle` | `0x417B8524` |
| `XHttpConnect` | `0x417B8538` |
| `XHttpSetStatusCallback` | `0x417B856C` |
| `XHttpSendRequest` | `0x417B85A0` |
| `XHttpReceiveResponse` | `0x417B85EC` |
| `XHttpQueryHeaders` | `0x417B8604` |
| `XHttpReadData` | `0x417B8648` |
| `XHttpWriteData` | `0x417B867C` |
| `XHttpQueryOption` | `0x417B86B0` |
| `XHttpSetOption` | `0x417B86E4` |
| `XHttpDoWork` | `0x417B8718` |
| `XHttpSetCredentials` | `0x417B8730` |
| `XHttpQueryDataAvailable` | `0x417B8774` |
| `XHttpStartup` | `0x417B878C` |
| `XHttpShutdown` | `0x417B88A8` |
| `XHttpOpenRequest` | `0x417B8A30` |

## `wzcsapi.dll` — 14 exports

| Export | VA |
|---|---|
| `WZCPassword2Key` | `0x4055149C` |
| `WZCEnumInterfaces` | `0x40551538` |
| `WZCQueryInterface` | `0x4055197C` |
| `WZCQueryInterfaceEx` | `0x4055199C` |
| `WZCDeleteIntfObj` | `0x40551A64` |
| `WZCDeleteIntfObjEx` | `0x40551A78` |
| `WZCSetInterface` | `0x40551B00` |
| `WZCSetInterfaceEx` | `0x40551B20` |
| `WZCRefreshInterface` | `0x40551B40` |
| `WZCRefreshInterfaceEx` | `0x40551B9C` |
| `WZCEnumEapExtensions` | `0x40551BAC` |
| `WZCQueryContext` | `0x40551D30` |
| `WZCSetContext` | `0x40551D6C` |
| `WzcPortIndication` | `0x40551DA0` |

## `ZWmtStreamer.dll` — 5 exports

| Export | VA |
|---|---|
| `DllMain` | `0x41B470A0` |
| `DllCanUnloadNow` | `0x41B574DC` |
| `DllGetClassObject` | `0x41B57588` |
| `DllRegisterServer` | `0x41B580DC` |
| `DllUnregisterServer` | `0x41B580DC` |

