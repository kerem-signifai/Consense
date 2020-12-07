import React, {Component} from 'react';
import {Container, Popup} from "semantic-ui-react";

const RING_X = 560
const RING_Y = 510
const RING_R = 350
const TEXT_BUFFER = 70
const NODE_R = 40
const MSG_R = 20

const PHASE_DURATION = 1000

class Node extends Component {
    render() {
        const {posX, posY, id, corrupt, textX, textY, nodeState, output} = this.props;
        return [
            <text key={id + '-text'} className='node-id' x={textX} y={textY}>{id}</text>,
            <Popup
                basic
                wide
                flowing
                on='click'
                key={id + '-state'}
                trigger={
                    <g>
                        <circle key={id + '-node'} className={"node" + (corrupt ? " corrupt" : "")} cx={posX} cy={posY} r={NODE_R}/>
                        {(output != null) ? <circle key={id + '-output'} cx={posX} cy={posY} r={NODE_R} stroke="green" strokeWidth="7" fill="none"/> : null}
                    </g>
                }
            >
                <pre><strong>Output: </strong>{JSON.stringify(output)}</pre>
                <pre><strong>State: </strong>{JSON.stringify(nodeState, null, 2)}</pre>
            </Popup>
        ]
    }
}

class Message extends Component {
    state = {
        posX: this.props.fromX,
        posY: this.props.fromY
    }
    animationId = null;
    drawStart = null;
    draw = (timestamp) => {
        const {fromX, fromY, toX, toY} = this.props;

        const elapsed = timestamp - this.drawStart;
        const progress = Math.min(elapsed, PHASE_DURATION) / PHASE_DURATION;
        const distX = toX - fromX;
        const distY = toY - fromY;

        this.setState({
            posX: fromX + (progress * distX),
            posY: fromY + (progress * distY)
        });

        this.animationId = window.requestAnimationFrame(this.draw);
    }

    componentDidMount = () => {
        this.animationId = window.requestAnimationFrame(ts => {
            this.drawStart = ts;
            this.draw(this.drawStart);
        });
    };

    componentWillUnmount = () => {
        window.cancelAnimationFrame(this.animationId);
    };

    render = () => {
        return <circle className="message" cx={this.state.posX} cy={this.state.posY} r={MSG_R}/>
    };
}

export default class Simulation extends Component {
    state = {
        progress: 0,
        maxProgress: 0,
        round: 0,
        phase: 'after',
        curMessages: [],
        nodeStates: {},
        nodes: [],
        nodePositions: {},
        terminatedNodes: [],
        nodeOutputs: {}
    }

    componentWillUnmount = () => {
      clearInterval(this.roundTimer);
    };

    componentDidMount = () => {
        const {data} = this.props;
        const nodes = [];
        const nodePositions = {};
        const a = (2.0 * Math.PI) / data.numNodes;
        for (let i = 1; i <= data.numNodes; i++) {
            const x = RING_X + RING_R * Math.cos(a * i);
            const y = RING_Y + RING_R * Math.sin(a * i);

            const textX = RING_X + (RING_R + TEXT_BUFFER) * Math.cos(a * i);
            const textY = RING_Y + (RING_R + TEXT_BUFFER) * Math.sin(a * i);
            nodePositions[i] = {
                x: x,
                y: y,
                textX: textX,
                textY: textY
            };
        }

        for (let i = 1; i <= data.numNodes; i++) {
            const {x, y, textX, textY} = nodePositions[i];
            nodes.push(<Node key={i} posX={x} posY={y} textX={textX} textY={textY} id={i} corrupt={data.corruptNodes.includes(i)}/>)
        }

        this.setState({
            nodes: nodes,
            nodePositions: nodePositions,
            maxProgress: Object.keys(data.actions).length * 3
        })

        this.roundTimer = setInterval(() => {
            let newRound = this.state.round;
            let newPhase = this.state.phase;
            let curProgress = this.state.progress;
            if (this.state.phase === 'after') {
                if (!this.props.data.actions[this.state.round + 1]) {
                    clearInterval(this.roundTimer);
                    return;
                } else {
                    newRound = this.state.round + 1;
                    newPhase = 'before';
                    curProgress++;
                }
            } else if (this.state.phase === 'during') {
                newPhase = 'after';
                curProgress++;
            } else if (this.state.phase === 'before') {
                newPhase = 'during';
                curProgress++;
            }

            const curMessages = [];
            if (data.actions[newRound]) {
                const curActions = this.props.data.actions[newRound][newPhase];
                let idx = 0;
                curActions.forEach(ac => {
                    if (ac.action === 'message_sent') {
                        const from = this.state.nodePositions[ac.from];
                        const to = this.state.nodePositions[ac.to];
                        const payload = ac.payload;
                        curMessages.push(<Message key={ac.from + ':' + ac.to + ':' + idx++ + ':' + this.state.round + ':' + this.state.phase} payload={payload} fromX={from.x} fromY={from.y} toX={to.x}
                                                  toY={to.y}/>);
                    } else if (ac.action === 'state_changed') {
                        this.setState({
                            nodeStates: {
                                ...this.state.nodeStates,
                                [ac.node]: ac.newState
                            }
                        })
                    } else if (ac.action === 'terminated') {
                        this.setState({
                            terminatedNodes: [...this.state.terminatedNodes, ac.node]
                        })
                    } else if (ac.action === 'output') {
                        this.setState({
                           nodeOutputs: {
                               ...this.state.nodeOutputs,
                               [ac.node]: ac.output
                           }
                        });
                    }
                })
            }

            const nodes = [];
            for (let i = 1; i <= data.numNodes; i++) {
                const {x, y, textX, textY} = nodePositions[i];
                nodes.push(<Node
                    key={i}
                    posX={x}
                    posY={y}
                    textX={textX}
                    textY={textY}
                    id={i}
                    corrupt={data.corruptNodes.includes(i)}
                    nodeState={this.state.nodeStates[i]}
                    output={this.state.nodeOutputs[i]}
                    terminated={this.state.terminatedNodes.includes(i)}
                />)
            }

            this.setState({
                progress: curProgress,
                round: newRound,
                phase: newPhase,
                curMessages: curMessages,
                nodes: nodes
            });
        }, PHASE_DURATION);
    };

    render = () => {
        const {curMessages, nodes} = this.state;

        return (
            <Container textAlign='center' className='sim-container'>
                <h1>Round {this.state.round}</h1>
                <svg width={1200} height={1000} className="canvas">
                    <circle className="ring" cx={RING_X} cy={RING_Y} r={RING_R}/>
                    <g id='messages'>{curMessages}</g>
                    <g id='nodes'>{nodes}</g>
                </svg>
            </Container>
        )
    };
}